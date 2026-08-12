package com.desitech.vyaparsathi.delivery.dto;

import lombok.Data;

@Data
public class DeliveryPersonDTO {
    private Long id;
    private String name;
    private String phone;
    private String notes;
    private String vehicleNumber;
    private String licenseNumber;
    private String employeeId;
    private Boolean active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
