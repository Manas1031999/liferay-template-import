package com.liferay.template.importer.action;

import com.liferay.dynamic.data.mapping.constants.DDMTemplateConstants;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.dynamic.data.mapping.service.DDMTemplateLocalService;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.service.ClassNameLocalServiceUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.upload.UploadPortletRequest;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.template.importer.constants.LiferayTemplateImportPortletKeys;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletException;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
@Component(
	    property = {
	        "javax.portlet.name="+LiferayTemplateImportPortletKeys.LIFERAYTEMPLATEIMPORT,
	        "mvc.command.name=/import/templates"
	    },
	    service = MVCActionCommand.class
	)
public class ImportTemplatesMVCActionCommand implements MVCActionCommand{
	
	private static final Log _log = LogFactoryUtil.getLog(ImportTemplatesMVCActionCommand.class);

    @Reference
    private DDMTemplateLocalService ddmTemplateLocalService;

    @Reference
    private DDMStructureLocalService ddmStructureLocalService;


	@Override
	public boolean processAction(ActionRequest actionRequest, ActionResponse actionResponse) throws PortletException {
		UploadPortletRequest uploadRequest = PortalUtil.getUploadPortletRequest(actionRequest);
        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(com.liferay.portal.kernel.util.WebKeys.THEME_DISPLAY);

        boolean overwriteExisting = com.liferay.portal.kernel.util.ParamUtil.getBoolean(uploadRequest, "overwriteExisting", true);
        long targetGroupId = com.liferay.portal.kernel.util.ParamUtil.getLong(uploadRequest, "targetGroupId", themeDisplay.getScopeGroupId());
        long targetUserId = themeDisplay.getUserId();

        InputStream csvStream = null;
        InputStream zipStream = null;

        try {
            csvStream = uploadRequest.getFileAsStream("meta_csv");
            zipStream = uploadRequest.getFileAsStream("templates_zip");

            if (csvStream == null) {
                SessionErrors.add(actionRequest, "no-csv-uploaded");
                actionRequest.setAttribute("importExceptionMessage", "meta.csv is required.");
                return true;
            }

            List<Map<String, String>> rows = CSVUtils.parseToMaps(csvStream);
            Map<String, byte[]> zipEntries = ZipUtils.readAllEntries(zipStream);

            int successCount = 0;
            int failCount = 0;
            StringBuilder failReasons = new StringBuilder();

            ServiceContext serviceContext = new ServiceContext();
            serviceContext.setScopeGroupId(targetGroupId);
            serviceContext.setAddGroupPermissions(true);
            serviceContext.setAddGuestPermissions(true);
            serviceContext.setUserId(targetUserId);

            for (Map<String, String> row : rows) {
                String nameRaw = row.getOrDefault("name", "").trim();
                String templateIdStr = row.getOrDefault("templateId", "").trim();
                String groupIdStr = row.getOrDefault("groupId", String.valueOf(targetGroupId)).trim();
                String structureIdStr = row.getOrDefault("structureId", "").trim();
                String classNameFull = row.getOrDefault("classNameFull", "").trim();
                String templateKey = row.getOrDefault("templateKey", "").trim();
                String externalReferenceCode = row.getOrDefault("externalReferenceCode", "").trim();

                // Normalize name and candidate keys
                String nameNoExt = nameRaw.endsWith(".ftl") ? nameRaw.substring(0, nameRaw.length() - 4) : nameRaw;
                
             // ---------- canonical displayName (no .ftl) — no generated fallbacks ----------
                String displayName = null;

                // Prefer explicit templateKey / ERC if provided — strip ".ftl" if present and trim
                if (!Validator.isNull(nameNoExt)) {
                    displayName = nameNoExt.trim();
                } else {
                    displayName = ""; // intentionally empty if nothing provided
                }

                // Remove trailing ".ftl" if present (case-insensitive)
                if (!Validator.isNull(displayName) && displayName.toLowerCase().endsWith(".ftl")) {
                    displayName = displayName.substring(0, displayName.length() - 4).trim();
                }

                // If after stripping we have an empty displayName, skip this row (no autogenerated keys)
                if (Validator.isNull(displayName)) {
                    failCount++;
                    failReasons.append("Row missing usable name/templateKey/externalReferenceCode (after removing .ftl); ");
                    _log.warn("Skipping CSV row because no usable name/templateKey/ERC after normalization.");
                    continue; // skip this row
                }

                // Now ensure templateKey and externalReferenceCode have sensible values (use displayName)
                if (Validator.isNull(templateKey)) {
                    templateKey = displayName;
                }
                if (Validator.isNull(externalReferenceCode)) {
                    externalReferenceCode = displayName;
                }


                long groupId = targetGroupId;
                try {
                    if (!groupIdStr.isEmpty()) {
                        groupId = Long.parseLong(groupIdStr);
                    }
                } catch (NumberFormatException e) {
                    _log.warn("Invalid groupId '" + groupIdStr + "', using targetGroupId: " + targetGroupId);
                    groupId = targetGroupId;
                }

                if (Validator.isNull(nameRaw) && Validator.isNull(templateKey) && Validator.isNull(externalReferenceCode)) {
                    failCount++;
                    failReasons.append("Row missing name/templateKey/externalReferenceCode; ");
                    continue;
                }

                // Candidate key lookup order
                String[] candidates = new String[] {
                    externalReferenceCode + ".ftl",
                    externalReferenceCode,
                    templateKey + ".ftl",
                    templateKey,
                    nameRaw,
                    nameNoExt,
                    nameRaw + ".ftl",
                    nameNoExt + ".ftl"
                };

                byte[] scriptBytes = null;
                String matchedKey = null;
                for (String cand : candidates) {
                    if (cand == null || cand.isEmpty()) continue;
                    if (zipEntries.containsKey(cand)) {
                        scriptBytes = zipEntries.get(cand);
                        matchedKey = cand;
                        break;
                    }
                    if (zipEntries.containsKey(cand.toLowerCase())) {
                        scriptBytes = zipEntries.get(cand.toLowerCase());
                        matchedKey = cand.toLowerCase();
                        break;
                    }
                    // check basename
                    String base = cand;
                    int lastSlash = base.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        base = base.substring(lastSlash + 1);
                    }
                    if (zipEntries.containsKey(base)) {
                        scriptBytes = zipEntries.get(base);
                        matchedKey = base;
                        break;
                    }
                    if (zipEntries.containsKey(base.toLowerCase())) {
                        scriptBytes = zipEntries.get(base.toLowerCase());
                        matchedKey = base.toLowerCase();
                        break;
                    }
                    if (base.endsWith(".ftl")) {
                        String noext = base.substring(0, base.length() - 4);
                        if (zipEntries.containsKey(noext)) {
                            scriptBytes = zipEntries.get(noext);
                            matchedKey = noext;
                            break;
                        }
                        if (zipEntries.containsKey(noext.toLowerCase())) {
                            scriptBytes = zipEntries.get(noext.toLowerCase());
                            matchedKey = noext.toLowerCase();
                            break;
                        }
                    }
                }

                if (scriptBytes == null) {
                    failCount++;
                    failReasons.append((nameRaw.isEmpty() ? templateKey : nameRaw)).append(": FTL not found in zip; ");
                    _log.warn("No FTL found in zip for CSV row: name=" + nameRaw + ", templateKey=" + templateKey + ", externalReferenceCode=" + externalReferenceCode);
                    continue;
                }

                String script = new String(scriptBytes, StandardCharsets.UTF_8);

                // classNameId: CSV MUST provide classNameFull per-row (or you can derive it outside)
                if (Validator.isNull(classNameFull)) {
                    failCount++;
                    failReasons.append((nameRaw.isEmpty() ? templateKey : nameRaw)).append(": missing classNameFull; ");
                    continue;
                }
                long classNameId = ClassNameLocalServiceUtil.getClassNameId(classNameFull);

                // classPK for web content if provided (numeric structure id)
                long classPK = 0L;
                if (!structureIdStr.isEmpty()) {
                    try {
                        long structId = Long.parseLong(structureIdStr);
                        DDMStructure structure = ddmStructureLocalService.fetchDDMStructure(structId);
                        if (structure != null) {
                            classPK = structure.getStructureId();
                        } else {
                            _log.warn("Structure not found for id " + structId + " -- using classPK=0");
                        }
                    } catch (NumberFormatException nfe) {
                        _log.warn("Invalid structureId '" + structureIdStr + "'");
                    }
                }

                // Compute resourceClassNameId (must NOT be 0)
                String resourceClassName = row.getOrDefault("resourceClassName", "").trim();
                if (Validator.isNull(resourceClassName)) {
                    // derive from classNameFull: DDMStructure -> JournalArticle, otherwise PortletDisplayTemplate
                    if (classNameFull.contains("DDMStructure") || classNameFull.endsWith("DDMStructure")) {
                        resourceClassName = "com.liferay.journal.model.JournalArticle";
                    } else {
                        resourceClassName = "com.liferay.portlet.display.template.PortletDisplayTemplate";
                    }
                }

                long resourceClassNameId = ClassNameLocalServiceUtil.getClassNameId(resourceClassName);
                // safety fallback
                if (resourceClassNameId == 0L) {
                    resourceClassName = "com.liferay.portlet.display.template.PortletDisplayTemplate";
                    resourceClassNameId = ClassNameLocalServiceUtil.getClassNameId(resourceClassName);
                }

                // find existing template: ERC -> templateKey -> numeric templateId -> name+classNameId scan
                DDMTemplate existingTemplate = null;

                // 1) ERC
                if (!Validator.isNull(externalReferenceCode)) {
                    try {
                        //existingTemplate = ddmTemplateLocalService.fetchDDMTemplateByExternalReferenceCode(externalReferenceCode, groupId);
                    } catch (NoSuchMethodError nsme) {
                        // ignore on older runtimes
                    } catch (Throwable t) {
                        _log.warn("Error fetching by ERC: " + t.getMessage(), t);
                    }
                }

                // 2) templateKey lookup (works in many runtimes via util)
                if (existingTemplate == null && !Validator.isNull(templateKey)) {
                    try {
                        existingTemplate = com.liferay.dynamic.data.mapping.service.DDMTemplateLocalServiceUtil.fetchTemplate(groupId, classNameId, templateKey);
                    } catch (NoSuchMethodError nsme) {
                        // ignore
                    } catch (Throwable t) {
                        _log.warn("Error fetching by templateKey: " + t.getMessage(), t);
                    }
                }

                // 3) numeric templateId
                if (existingTemplate == null && !Validator.isNull(templateIdStr)) {
                    try {
                        long templId = Long.parseLong(templateIdStr);
                        existingTemplate = ddmTemplateLocalService.fetchDDMTemplate(templId);
                    } catch (NumberFormatException nfe) {
                        // ignore
                    } catch (Throwable t) {
                        _log.warn("Error fetching by templateId: " + t.getMessage(), t);
                    }
                }

                // 4) fallback: scan group templates by name + classNameId
                if (existingTemplate == null) {
                    try {
                        List<DDMTemplate> groupTemplates = ddmTemplateLocalService.getTemplates(groupId);
                        for (DDMTemplate t : groupTemplates) {
                            String tName = t.getName(LocaleUtil.getDefault());
                            if (tName != null && tName.equals(nameRaw) && t.getClassNameId() == classNameId) {
                                existingTemplate = t;
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        _log.warn("Error scanning templates: " + t.getMessage(), t);
                    }
                }

                // update or create
                if (existingTemplate != null) {
                    if (overwriteExisting) {
                        try {
                            existingTemplate.setScript(script);
                            existingTemplate.setLanguage("ftl");
                            existingTemplate.setType(DDMTemplateConstants.TEMPLATE_TYPE_DISPLAY);
                            existingTemplate.setMode(DDMTemplateConstants.TEMPLATE_MODE_CREATE);
                            existingTemplate.setClassPK(classPK);
                            existingTemplate.setResourceClassNameId(resourceClassNameId);

                            Map<Locale, String> nm = existingTemplate.getNameMap();
                            if (nm == null) nm = new HashMap<>();
                            nm.put(LocaleUtil.getDefault(), displayName);
                            nm.put(themeDisplay.getLocale(), displayName);
                            existingTemplate.setNameMap(nm);

                            Map<Locale, String> dm = existingTemplate.getDescriptionMap();
                            if (dm == null) dm = new HashMap<>();
                            dm.put(LocaleUtil.getDefault(), "Imported/updated by CSV+ZIP importer");
                            existingTemplate.setDescriptionMap(dm);

                            ddmTemplateLocalService.updateDDMTemplate(existingTemplate);

                            successCount++;
                            _log.info("Updated template id=" + existingTemplate.getTemplateId() + " (matched zip key: " + matchedKey + ")");
                        } catch (Exception e) {
                            failCount++;
                            failReasons.append("Update failed for " + (nameRaw.isEmpty() ? templateKey : nameRaw) + ": " + e.getMessage() + "; ");
                            _log.error("Update failed", e);
                        }
                    } else {
                        failCount++;
                        failReasons.append((nameRaw.isEmpty() ? templateKey : nameRaw)).append(": exists and overwrite disabled; ");
                    }
                } else {
                    try {
                    	
                    	Map<Locale, String> nameMap = new HashMap<>();
                    	nameMap.put(LocaleUtil.getDefault(), displayName);
                    	nameMap.put(themeDisplay.getLocale(), displayName);

                    	Map<Locale, String> descMap = new HashMap<>();
                    	descMap.put(LocaleUtil.getDefault(), "Imported by CSV+ZIP importer");
                    	descMap.put(themeDisplay.getLocale(), "Imported by CSV+ZIP importer");
                    	
                        /*DDMTemplate created = ddmTemplateLocalService.addTemplate(
                            (Validator.isNull(externalReferenceCode) ? null : externalReferenceCode),
                            serviceContext.getUserId(),
                            groupId,
                            classNameId,
                            classPK,
                            resourceClassNameId, // <--- important: valid id (not 0)
                            nameMap,
                            descMap,
                            DDMTemplateConstants.TEMPLATE_TYPE_DISPLAY,
                            DDMTemplateConstants.TEMPLATE_MODE_CREATE,
                            "ftl",
                            script,
                            serviceContext
                        );*/

                        successCount++;
                        //_log.info("Created template id=" + created.getTemplateId() + " (matched zip key: " + matchedKey + ")");
                    } catch (Exception e) {
                        failCount++;
                        failReasons.append("Create failed for " + (nameRaw.isEmpty() ? templateKey : nameRaw) + ": " + e.getMessage() + "; ");
                        _log.error("Create failed", e);
                    }
                }
            } // end rows loop

            if (failCount == 0) {
                SessionMessages.add(actionRequest, "import-success");
            } else {
                actionRequest.setAttribute("importExceptionMessage", "Imported: " + successCount + ", Failed: " + failCount + ". Details: " + failReasons.toString());
                SessionErrors.add(actionRequest, "import-failed");
            }

        } catch (Exception e) {
            _log.error("ImportAction failed: " + e.getMessage(), e);
            SessionErrors.add(actionRequest, "import-failed");
            actionRequest.setAttribute("importExceptionMessage", "Import failed: " + e.getMessage());

            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            actionRequest.setAttribute("importFullErrorTrace", sw.toString());
        } finally {
            try { if (csvStream != null) csvStream.close(); } catch (Exception ignored) {}
            try { if (zipStream != null) zipStream.close(); } catch (Exception ignored) {}
        }

        // render view
        actionResponse.setRenderParameter("mvcPath", "/view.jsp");
        return true;
	}

}
