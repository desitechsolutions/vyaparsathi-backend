package com.desitech.vyaparsathi.auth.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class ResetTokenRequest {
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
