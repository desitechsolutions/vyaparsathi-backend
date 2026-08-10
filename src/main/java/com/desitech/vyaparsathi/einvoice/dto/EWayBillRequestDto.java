package com.desitech.vyaparsathi.einvoice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EWayBillRequestDto {
    @NotNull(message = "Sale ID is required")
    private Long saleId;

    private String vehicleNumber; // e.g. "DL01AB1234"
    private String transporterId; // Transporter GSTIN or GSTD ID
    private String transporterName;
    private Integer distanceKm = 100; // Transport distance in km
    private String modeOfTransport = "ROAD"; // ROAD, RAIL, AIR, SHIP

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getTransporterId() { return transporterId; }
    public void setTransporterId(String transporterId) { this.transporterId = transporterId; }

    public String getTransporterName() { return transporterName; }
    public void setTransporterName(String transporterName) { this.transporterName = transporterName; }

    public Integer getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Integer distanceKm) { this.distanceKm = distanceKm; }

    public String getModeOfTransport() { return modeOfTransport; }
    public void setModeOfTransport(String modeOfTransport) { this.modeOfTransport = modeOfTransport; }

    public String getTransportMode() { return modeOfTransport; }
    public void setTransportMode(String transportMode) { this.modeOfTransport = transportMode; }
}
