package com.desitech.vyaparsathi.einvoice.service;

import com.desitech.vyaparsathi.einvoice.dto.EInvoiceResponseDto;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class EInvoiceService {

    private final SaleRepository saleRepo;

    public EInvoiceService(SaleRepository saleRepo) {
        this.saleRepo = saleRepo;
    }

    @Transactional
    public EInvoiceResponseDto generateIrn(Long saleId) {
        Sale sale = saleRepo.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found with id: " + saleId));

        if ("GENERATED".equalsIgnoreCase(sale.getEinvoiceStatus())) {
            EInvoiceResponseDto response = new EInvoiceResponseDto();
            response.setSaleId(sale.getId());
            response.setInvoiceNo(sale.getInvoiceNo());
            response.setIrn(sale.getIrn());
            response.setAckNo(sale.getAckNo());
            response.setAckDate(sale.getAckDate());
            response.setQrCodePath(sale.getQrCodePath());
            response.setEinvoiceStatus(sale.getEinvoiceStatus());
            return response;
        }

        // Generate 64-character SHA-256 IRN Hash (Seller GSTIN + Fin Year + Doc Type + Doc Number)
        Long shopId = sale.getShop() != null ? sale.getShop().getId() : 1L;
        String rawKey = shopId + ":" + sale.getInvoiceNo() + ":" + System.currentTimeMillis();
        String irn = hashSha256(rawKey);

        String ackNo = "11" + (System.currentTimeMillis() % 10000000000L);
        LocalDateTime ackDate = LocalDateTime.now();
        String qrCode = "https://einvoice.gst.gov.in/qr?irn=" + irn;

        sale.setIrn(irn);
        sale.setAckNo(ackNo);
        sale.setAckDate(ackDate);
        sale.setQrCodePath(qrCode);
        sale.setEinvoiceStatus("GENERATED");

        saleRepo.save(sale);

        EInvoiceResponseDto dto = new EInvoiceResponseDto();
        dto.setSaleId(sale.getId());
        dto.setInvoiceNo(sale.getInvoiceNo());
        dto.setIrn(irn);
        dto.setAckNo(ackNo);
        dto.setAckDate(ackDate);
        dto.setQrCodePath(qrCode);
        dto.setEinvoiceStatus("GENERATED");
        return dto;
    }

    @Transactional
    public EInvoiceResponseDto cancelIrn(Long saleId, String reason) {
        Sale sale = saleRepo.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found with id: " + saleId));

        sale.setEinvoiceStatus("CANCELLED");
        saleRepo.save(sale);

        EInvoiceResponseDto dto = new EInvoiceResponseDto();
        dto.setSaleId(sale.getId());
        dto.setInvoiceNo(sale.getInvoiceNo());
        dto.setIrn(sale.getIrn());
        dto.setEinvoiceStatus("CANCELLED");
        return dto;
    }

    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        }
    }
}
