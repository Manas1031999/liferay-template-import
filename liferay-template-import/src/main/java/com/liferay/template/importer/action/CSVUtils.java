package com.liferay.template.importer.action;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class CSVUtils {
	
	public static List<Map<String, String>> parseToMaps(InputStream csvStream) throws Exception {
        byte[] bytes = csvStream.readAllBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);

        // quick normalization: if entire content is wrapped in double-quotes, unwrap it
        String trimmed = content.trim();
        if (trimmed.length() > 1 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            // remove outer quotes and try to replace internal literal "\"\n\"" patterns (Excel quirks)
            trimmed = trimmed.substring(1, trimmed.length() - 1).replaceAll("\"\\r?\\n\"", "\n");
            content = trimmed;
        }

        // Try commons-csv first
        try (Reader r = new StringReader(content);
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim().parse(r)) {

            // If headers look suspicious (single header containing commas) then fall back
            List<String> headerNames = parser.getHeaderNames();
            boolean badHeader = (headerNames == null || headerNames.size() == 1 && headerNames.get(0).contains(","));

            if (!badHeader) {
                List<Map<String, String>> rows = new ArrayList<>();
                for (CSVRecord record : parser) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String header : headerNames) {
                        row.put(header.trim(), record.get(header));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (Exception ignored) {
            // fallthrough to fallback parser
        }

        // Fallback: do a simple but robust split: split into lines then split each line respecting quotes
        String[] lines = content.split("\\r?\\n");
        if (lines.length == 0) {
            return List.of();
        }

        List<String> headers = splitLine(lines[0]);
        List<Map<String, String>> rows = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) continue;
            List<String> values = splitLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int h = 0; h < headers.size(); h++) {
                String key = headers.get(h).trim();
                String val = (h < values.size()) ? values.get(h) : "";
                row.put(key, val);
            }
            rows.add(row);
        }

        return rows;
    }

    // Splits a CSV line into fields honoring double-quotes
    private static List<String> splitLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                // support double-quote escape "" -> "
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (c == ',' && !inQuote) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

}
