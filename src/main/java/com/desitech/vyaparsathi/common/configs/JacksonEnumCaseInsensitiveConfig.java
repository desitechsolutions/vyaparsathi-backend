package com.desitech.vyaparsathi.common.configs;

import com.fasterxml.jackson.databind.MapperFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

@Configuration
public class JacksonEnumCaseInsensitiveConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer caseInsensitiveEnumCustomizer() {
        return builder -> builder.featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }
}