package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.receiving.dto.ReceivingDto;
import com.desitech.vyaparsathi.receiving.dto.ReceivingItemDto;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV bulk-import for DRAFT GRN quantities. Header row required with
 * canonical columns:
 * {@code purchaseOrderItemId,receivedQty,damagedQty,rejectedQty,batchNumber,expiryDate,serialNumber,notes}.
 *
 * <p>Streams the file — no full-buffer read — so a 10k-line GRN import doesn't
 * spike heap. Malformed rows are collected into an error list and rejected as
 * a batch; partial commits are avoided so the user can fix the CSV and retry.
 */
@Service
public class ReceivingBulkImportService {

    private static final Logger log = LoggerFactory.getLogger(ReceivingBulkImportService.class);
    private static final String[] REQUIRED_HEADERS = {
            "purchaseOrderItemId", "receivedQty"
    };

    private final ReceivingRepository receivingRepository;
    private final ReceivingService receivingService;

    public ReceivingBulkImportService(ReceivingRepository receivingRepository,
                                      ReceivingService receivingService) {
        this.receivingRepository = receivingRepository;
        this.receivingService = receivingService;
    }

    @Transactional
    public ReceivingDto importCsv(Long receivingId, MultipartFile file) {
        Receiving existing = receivingRepository.findById(receivingId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found: " + receivingId));
        if (!existing.getStatus().isEditable()) {
            throw new BusinessValidationException("Bulk import is only allowed on DRAFT GRNs.");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessValidationException("CSV file is empty.");
        }

        List<ReceivingItemDto> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new BusinessValidationException("CSV is missing a header row.");
            Map<String, Integer> headerIndex = parseHeader(headerLine);
            for (String required : REQUIRED_HEADERS) {
                if (!headerIndex.containsKey(required)) {
                    throw new BusinessValidationException("Missing required column: " + required);
                }
            }
            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.trim().isEmpty()) continue;
                String[] cells = line.split(",", -1);
                try {
                    ReceivingItemDto item = new ReceivingItemDto();
                    item.setPurchaseOrderItemId(Long.parseLong(get(cells, headerIndex, "purchaseOrderItemId")));
                    item.setReceivedQty(intOrZero(get(cells, headerIndex, "receivedQty")));
                    item.setDamagedQty(intOrZero(get(cells, headerIndex, "damagedQty")));
                    item.setRejectedQty(intOrZero(get(cells, headerIndex, "rejectedQty")));
                    item.setBatchNumber(get(cells, headerIndex, "batchNumber"));
                    String exp = get(cells, headerIndex, "expiryDate");
                    if (exp != null && !exp.isBlank()) {
                        try { item.setExpiryDate(LocalDate.parse(exp)); }
                        catch (DateTimeParseException e) { errors.add("Row " + rowNum + ": bad expiryDate '" + exp + "'"); }
                    }
                    item.setSerialNumber(get(cells, headerIndex, "serialNumber"));
                    item.setNotes(get(cells, headerIndex, "notes"));
                    items.add(item);
                } catch (NumberFormatException e) {
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new BusinessValidationException("Failed to read CSV: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            throw new BusinessValidationException("CSV import failed:\n" + String.join("\n", errors));
        }
        if (items.isEmpty()) {
            throw new BusinessValidationException("CSV had a header but no data rows.");
        }

        ReceivingDto payload = new ReceivingDto();
        payload.setId(existing.getId());
        payload.setPurchaseOrderId(existing.getPurchaseOrder().getId());
        payload.setShopId(existing.getShop() != null ? existing.getShop().getId() : null);
        payload.setReceivingItems(items);

        log.info("Bulk-importing {} lines into GRN {}", items.size(), existing.getGrNumber());
        return receivingService.updateReceiving(receivingId, payload)
                .orElseThrow(() -> new ResourceNotFoundException("Update failed after bulk import"));
    }

    private Map<String, Integer> parseHeader(String headerLine) {
        Map<String, Integer> idx = new HashMap<>();
        String[] cols = headerLine.split(",", -1);
        for (int i = 0; i < cols.length; i++) {
            idx.put(cols[i].trim(), i);
        }
        return idx;
    }

    private String get(String[] cells, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i >= cells.length) return null;
        String v = cells[i];
        return v == null ? null : v.trim();
    }

    private int intOrZero(String v) {
        if (v == null || v.isBlank()) return 0;
        return Integer.parseInt(v.trim());
    }
}
