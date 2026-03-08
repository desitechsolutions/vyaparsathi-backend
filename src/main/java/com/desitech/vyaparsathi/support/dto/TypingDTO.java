package com.desitech.vyaparsathi.support.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TypingDTO {
    private Long shopId;
    private String userName;
    @JsonProperty("isTyping")
    private boolean isTyping;
}