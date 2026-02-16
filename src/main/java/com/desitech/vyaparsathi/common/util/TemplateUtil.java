package com.desitech.vyaparsathi.common.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.stream.Collectors;

public class TemplateUtil {

    public static String loadTemplate(String templatePath, Map<String, String> variables) {
        try (InputStream inputStream = TemplateUtil.class.getClassLoader().getResourceAsStream(templatePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Template not found: " + templatePath);
            }

            String content = new BufferedReader(new InputStreamReader(inputStream))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // Replace all variables like ${name}, ${resetLink}, etc.
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                content = content.replace("${" + entry.getKey() + "}", entry.getValue());
            }

            return content;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + templatePath, e);
        }
    }
}