package com.liferay.template.importer.action;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {
	public static Map<String, byte[]> readAllEntries(InputStream zipStream) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        if (zipStream == null) {
            return entries;
        }
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                byte[] data = baos.toByteArray();
                String entryName = entry.getName(); // e.g. templates/test-search-result.ftl
                // put raw key
                entries.put(entryName, data);

                // base filename (test-search-result.ftl)
                try {
                    String base = Paths.get(entryName).getFileName().toString();
                    entries.putIfAbsent(base, data);
                    // extensionless
                    int dot = base.lastIndexOf('.');
                    if (dot > 0) {
                        String baseNoExt = base.substring(0, dot);
                        entries.putIfAbsent(baseNoExt, data);
                        entries.putIfAbsent(baseNoExt.toLowerCase(), data);
                    }
                    entries.putIfAbsent(base.toLowerCase(), data);
                } catch (Exception ignore) {
                    // ignore Path issues on weird names; we already have raw entry
                }

                // also put lowercase raw path
                entries.putIfAbsent(entryName.toLowerCase(), data);
                zis.closeEntry();
            }
        }
        return entries;
    }
}
