package com.desitech.vyaparsathi.einvoice.service;

import com.desitech.vyaparsathi.einvoice.dto.EWayBillRequestDto;
import com.desitech.vyaparsathi.einvoice.dto.EWayBillResponseDto;
import com.desitech.vyaparsathi.einvoice.provider.EWayBillProvider;
import com.desitech.vyaparsathi.einvoice.provider.EWayBillProviderException;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Business layer for E-Way Bill generation. Owns:
 *   - Sale lookup + idempotency (already-generated? return the stored data).
 *   - The ₹50,000 threshold gate.
 *   - Persisting the EWB number, dates, and transporter/vehicle metadata onto
 *     the Sale row.
 *
 * Delegates the actual number generation to {@link EWayBillProvider}, so
 * swapping between the synthetic mock and a real NIC/GSP call is a config
 * change (see {@code vyaparsathi.eway.provider}).
 */
@Service
public class EWayBillService {

    private static final Logger logger = LoggerFactory.getLogger(EWayBillService.class);
    private static final BigDecimal EWB_THRESHOLD = new BigDecimal("50000");

    private final SaleRepository saleRepo;
    private final EWayBillProvider provider;

    public EWayBillService(SaleRepository saleRepo, EWayBillProvider provider) {
        this.saleRepo = saleRepo;
        this.provider = provider;
    }

    @Transactional
    public EWayBillResponseDto generateEWayBill(EWayBillRequestDto request) {
        Sale sale = saleRepo.findById(request.getSaleId())
                .orElseThrow(() -> new IllegalArgumentException("Sale not found with id: " + request.getSaleId()));

        if (sale.getGrandTotal().compareTo(EWB_THRESHOLD) < 0) {
            throw new IllegalArgumentException("E-Way Bill generation is required only for invoice values exceeding ₹50,000");
        }

        // Idempotency: if a number is already stamped on the Sale, return it
        // instead of asking the provider for a new one.
        if (sale.getEwayBillNo() != null) {
            return toResponse(sale, "GENERATED");
        }

        EWayBillProvider.Result result;
        try {
            result = provider.generate(sale, request);
        } catch (EWayBillProviderException e) {
            logger.error("EWB provider '{}' failed for saleId={}: {}", e.getProviderName(), sale.getId(), e.getMessage(), e);
            throw e;
        }

        sale.setEwayBillNo(result.getEwayBillNo());
        sale.setEwayBillDate(result.getGeneratedAt());
        sale.setEwayBillValidUntil(result.getValidUntil());
        sale.setVehicleNumber(request.getVehicleNumber());
        sale.setTransporterId(request.getTransporterId());
        sale.setTransporterName(request.getTransporterName());

        saleRepo.save(sale);
        logger.info("EWB generated via provider={} for saleId={} invoiceNo={}",
                provider.getProviderName(), sale.getId(), sale.getInvoiceNo());

        return toResponse(sale, "GENERATED");
    }

    private EWayBillResponseDto toResponse(Sale sale, String status) {
        EWayBillResponseDto dto = new EWayBillResponseDto();
        dto.setSaleId(sale.getId());
        dto.setInvoiceNo(sale.getInvoiceNo());
        dto.setEwayBillNo(sale.getEwayBillNo());
        dto.setEwayBillDate(sale.getEwayBillDate());
        dto.setValidUntil(sale.getEwayBillValidUntil());
        dto.setVehicleNumber(sale.getVehicleNumber());
        dto.setStatus(status);
        return dto;
    }
}
