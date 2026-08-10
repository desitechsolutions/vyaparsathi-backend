package com.desitech.vyaparsathi.einvoice.service;

import com.desitech.vyaparsathi.einvoice.dto.EWayBillRequestDto;
import com.desitech.vyaparsathi.einvoice.dto.EWayBillResponseDto;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class EWayBillService {

    private final SaleRepository saleRepo;

    public EWayBillService(SaleRepository saleRepo) {
        this.saleRepo = saleRepo;
    }

    @Transactional
    public EWayBillResponseDto generateEWayBill(EWayBillRequestDto request) {
        Sale sale = saleRepo.findById(request.getSaleId())
                .orElseThrow(() -> new IllegalArgumentException("Sale not found with id: " + request.getSaleId()));

        if (sale.getGrandTotal().compareTo(new BigDecimal("50000")) < 0) {
            throw new IllegalArgumentException("E-Way Bill generation is required only for invoice values exceeding ₹50,000");
        }

        if (sale.getEwayBillNo() != null) {
            EWayBillResponseDto response = new EWayBillResponseDto();
            response.setSaleId(sale.getId());
            response.setInvoiceNo(sale.getInvoiceNo());
            response.setEwayBillNo(sale.getEwayBillNo());
            response.setEwayBillDate(sale.getEwayBillDate());
            response.setValidUntil(sale.getEwayBillValidUntil());
            response.setVehicleNumber(sale.getVehicleNumber());
            response.setStatus("GENERATED");
            return response;
        }

        String ewayBillNo = "331" + (System.currentTimeMillis() % 1000000000L);
        LocalDateTime now = LocalDateTime.now();
        int daysValid = Math.max(1, (request.getDistanceKm() != null ? request.getDistanceKm() : 100) / 100);
        LocalDateTime validUntil = now.plusDays(daysValid);

        sale.setEwayBillNo(ewayBillNo);
        sale.setEwayBillDate(now);
        sale.setEwayBillValidUntil(validUntil);
        sale.setVehicleNumber(request.getVehicleNumber());
        sale.setTransporterId(request.getTransporterId());
        sale.setTransporterName(request.getTransporterName());

        saleRepo.save(sale);

        EWayBillResponseDto dto = new EWayBillResponseDto();
        dto.setSaleId(sale.getId());
        dto.setInvoiceNo(sale.getInvoiceNo());
        dto.setEwayBillNo(ewayBillNo);
        dto.setEwayBillDate(now);
        dto.setValidUntil(validUntil);
        dto.setVehicleNumber(request.getVehicleNumber());
        dto.setStatus("GENERATED");
        return dto;
    }
}
