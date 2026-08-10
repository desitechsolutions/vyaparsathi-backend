package com.desitech.vyaparsathi.notification.dto;

import lombok.Data;

@Data
public class ContactRequestDto {
    private String name;
    private String email;
    private String phone;
    private String company;
    private String serviceType;
    private String message;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}