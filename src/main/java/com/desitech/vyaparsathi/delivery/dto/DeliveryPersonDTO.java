package com.desitech.vyaparsathi.delivery.dto;

import lombok.Data;

@Data
public class DeliveryPersonDTO {
    public Long id;
    public String name;
    public String phone;
    public String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}