package com.liferay.template.importer.portlet;

public class TemplateTypeClassNames {
	
	private TemplateTypeClassNames() {}

	public static final String TEMPLATE_TYPE_ASSET_PUBLISHER = "asset-publisher";
    public static final String TEMPLATE_TYPE_SEARCH_RESULT = "search-result";
    public static final String TEMPLATE_TYPE_CATEGORY_FACET = "category-facet";
    public static final String TEMPLATE_TYPE_WEB_CONTENT = "web-content";

    // The actual class names requested (CNAME)
    public static final String CLASS_NAME_ASSET_ENTRY = "com.liferay.asset.kernel.model.AssetEntry";
    public static final String CLASS_NAME_SEARCH_RESULT =
        "com.liferay.portal.search.web.internal.result.display.context.SearchResultSummaryDisplayContext";
    public static final String CLASS_NAME_CATEGORY_FACET =
        "com.liferay.portal.search.web.internal.category.facet.portlet.CategoryFacetPortlet";
    public static final String CLASS_NAME_DDM_STRUCTURE = "com.liferay.dynamic.data.mapping.model.DDMStructure";

    // Resource class names (RCNAME) - these are the important ones for DDM permission/resource mapping
    public static final String RESOURCE_CLASS_NAME_PORTLET_DISPLAY_TEMPLATE =
        "com.liferay.portlet.display.template.PortletDisplayTemplate";
    public static final String RESOURCE_CLASS_NAME_JOURNAL_ARTICLE =
        "com.liferay.journal.model.JournalArticle";

}
