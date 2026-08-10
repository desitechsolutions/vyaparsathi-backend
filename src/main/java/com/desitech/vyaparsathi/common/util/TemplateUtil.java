package com.desitech.vyaparsathi.common.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.stream.Collectors;

public class TemplateUtil {

    public static String loadTemplate(String templatePath, Map<String, String> variables) {
        String path = templatePath.startsWith("/") ? templatePath.substring(1) : templatePath;
        InputStream inputStream = TemplateUtil.class.getClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            inputStream = TemplateUtil.class.getResourceAsStream("/" + path);
        }

        if (inputStream == null) {
            throw new RuntimeException("Template not found: " + templatePath);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String content = reader.lines().collect(Collectors.joining("\n"));

            // Replace all variables like ${name}, ${resetLink}, etc.
            if (variables != null) {
                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    content = content.replace("${" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
                }
            }

            return content;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + templatePath, e);
        }
    }
}