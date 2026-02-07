package com.desitech.vyaparsathi.auth.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // This maps the URL path `/media/item-photos/**` to the physical directory `uploads/item-photos/`
        // The "file:" prefix is crucial for serving from an external directory.
        String resourcePath = "/media/item-photos/**";
        String resourceLocation = "file:" + System.getProperty("user.dir") + "/uploads/item-photos/";

        registry.addResourceHandler(resourcePath)
                .addResourceLocations(resourceLocation);
    }
}