package com.desitech.vyaparsathi.purchases.service;

import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.service.StockService;
import com.desitech.vyaparsathi.purchases.dto.PurchaseCreateDto;
import com.desitech.vyaparsathi.purchases.dto.PurchaseInvoiceDto;
import com.desitech.vyaparsathi.purchases.dto.PurchaseInvoiceItemDto;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoiceItem;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceRepository;
import com.desitech.vyaparsathi.supplier.dto.SupplierDto;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import com.desitech.vyaparsathi.supplier.service.SupplierLedgerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository purchaseRepo;
    private final SupplierRepository supplierRepo;
    private final ItemVariantRepository variantRepo;
    private final StockService stockService;
    private final SupplierLedgerService ledgerService;

    public PurchaseInvoiceService(PurchaseInvoiceRepository purchaseRepo,
                                  SupplierRepository supplierRepo,
                                  ItemVariantRepository variantRepo,
                                  StockService stockService,
                                  SupplierLedgerService ledgerService) {
        this.purchaseRepo = purchaseRepo;
        this.supplierRepo = supplierRepo;
        this.variantRepo = variantRepo;
        this.stockService = stockService;
        this.ledgerService = ledgerService;
    }

    @Transactional
    @LogAudit(action = "CREATE_PURCHASE_INVOICE", entity = "PURCHASE_INVOICE")
    public PurchaseInvoiceDto createPurchaseInvoice(PurchaseCreateDto createDto) {
        Long shopId = TenantUtils.getCurrentShopId();
        Supplier supplier = supplierRepo.findById(createDto.getSupplierId())
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found with ID: " + createDto.getSupplierId()));

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setSupplier(supplier);
        invoice.setSupplierInvoiceNo(createDto.getSupplierInvoiceNo());
        invoice.setPurchaseDate(createDto.getPurchaseDate() != null ? createDto.getPurchaseDate() : LocalDate.now());
        invoice.setPaymentTerms(createDto.getPaymentTerms() != null ? createDto.getPaymentTerms() : "NET_30");
        invoice.setNotes(createDto.getNotes());

        // Generate unique purchase invoice sequence
        String purchaseNo = "PUR/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/" + System.currentTimeMillis() % 100000;
        invoice.setPurchaseInvoiceNo(purchaseNo);

        boolean isInterState = Boolean.TRUE.equals(createDto.getIsInterState());

        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalIgst = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        List<PurchaseInvoiceItem> itemsList = new ArrayList<>();

        for (var itemReq : createDto.getItems()) {
            ItemVariant variant = variantRepo.findById(itemReq.getItemVariantId())
                    .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + itemReq.getItemVariantId()));

            BigDecimal qty = itemReq.getQuantity();
            BigDecimal cost = itemReq.getUnitCost();
            BigDecimal disc = itemReq.getDiscount() != null ? itemReq.getDiscount() : BigDecimal.ZERO;
            BigDecimal gstRate = itemReq.getGstRate() != null ? itemReq.getGstRate() : BigDecimal.ZERO;

            BigDecimal lineGross = qty.multiply(cost).subtract(disc);
            if (lineGross.compareTo(BigDecimal.ZERO) < 0) lineGross = BigDecimal.ZERO;

            BigDecimal cgst = BigDecimal.ZERO;
            BigDecimal sgst = BigDecimal.ZERO;
            BigDecimal igst = BigDecimal.ZERO;

            if (gstRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalTax = lineGross.multiply(gstRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (isInterState) {
                    igst = totalTax;
                } else {
                    cgst = totalTax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    sgst = totalTax.subtract(cgst);
                }
            }

            BigDecimal lineTotal = lineGross.add(cgst).add(sgst).add(igst);

            PurchaseInvoiceItem line = new PurchaseInvoiceItem();
            line.setPurchaseInvoice(invoice);
            line.setItemVariant(variant);
            line.setItemName(variant.getItem() != null ? variant.getItem().getName() : "Item");
            line.setHsnCode(variant.getHsn());
            line.setBatchNumber(itemReq.getBatchNumber());
            line.setExpiryDate(itemReq.getExpiryDate());
            line.setQuantity(qty);
            line.setUnitCost(cost);
            line.setDiscount(disc);
            line.setTaxableAmount(lineGross);
            line.setGstRate(gstRate);
            line.setCgstAmount(cgst);
            line.setSgstAmount(sgst);
            line.setIgstAmount(igst);
            line.setLineTotal(lineTotal);

            itemsList.add(line);

            totalTaxable = totalTaxable.add(lineGross);
            totalCgst = totalCgst.add(cgst);
            totalSgst = totalSgst.add(sgst);
            totalIgst = totalIgst.add(igst);
            grandTotal = grandTotal.add(lineTotal);

            // Increment inventory stock
            com.desitech.vyaparsathi.inventory.dto.StockAddDto stockAddDto = new com.desitech.vyaparsathi.inventory.dto.StockAddDto();
            stockAddDto.setItemVariantId(variant.getId());
            stockAddDto.setQuantity(qty);
            stockAddDto.setCostPerUnit(cost);
            stockAddDto.setBatch(itemReq.getBatchNumber());
            stockAddDto.setExpiryDate(itemReq.getExpiryDate());
            stockService.addStockFromDto(stockAddDto);
        }

        invoice.setItems(itemsList);
        invoice.setTotalTaxableAmount(totalTaxable);
        invoice.setTotalCgst(totalCgst);
        invoice.setTotalSgst(totalSgst);
        invoice.setTotalIgst(totalIgst);
        invoice.setTotalAmount(grandTotal);

        // Initial payment logic
        BigDecimal initialPayment = createDto.getInitialPaymentAmount() != null ? createDto.getInitialPaymentAmount() : BigDecimal.ZERO;
        if (initialPayment.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setPaidAmount(initialPayment.min(grandTotal));
            if (invoice.getPaidAmount().compareTo(grandTotal) >= 0) {
                invoice.setPaymentStatus("PAID");
            } else {
                invoice.setPaymentStatus("PARTIAL");
            }
        } else {
            invoice.setPaidAmount(BigDecimal.ZERO);
            invoice.setPaymentStatus("PENDING");
        }

        PurchaseInvoice saved = purchaseRepo.save(invoice);

        // Record Vendor Credit Entry in Supplier Ledger
        ledgerService.recordEntry(supplier, "PURCHASE_INVOICE", saved.getPurchaseInvoiceNo(),
                BigDecimal.ZERO, grandTotal, "Purchase Invoice " + saved.getPurchaseInvoiceNo());

        // If initial payment was made, record Vendor Debit Entry
        if (initialPayment.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordEntry(supplier, "VENDOR_PAYMENT", saved.getPurchaseInvoiceNo(),
                    initialPayment, BigDecimal.ZERO, "Initial Payment via " + createDto.getPaymentMethod());
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseInvoiceDto> getAllPurchaseInvoices(Pageable pageable) {
        Long shopId = TenantUtils.getCurrentShopId();
        return purchaseRepo.findAllByShopId(shopId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public PurchaseInvoiceDto getPurchaseInvoiceById(Long id) {
        Long shopId = TenantUtils.getCurrentShopId();
        PurchaseInvoice invoice = purchaseRepo.findById(id)
                .filter(i -> i.getShop().getId().equals(shopId))
                .orElseThrow(() -> new IllegalArgumentException("Purchase Invoice not found: " + id));
        return toDto(invoice);
    }

    private PurchaseInvoiceDto toDto(PurchaseInvoice entity) {
        PurchaseInvoiceDto dto = new PurchaseInvoiceDto();
        dto.setId(entity.getId());
        dto.setPurchaseInvoiceNo(entity.getPurchaseInvoiceNo());
        dto.setSupplierInvoiceNo(entity.getSupplierInvoiceNo());

        if (entity.getSupplier() != null) {
            SupplierDto suppDto = new SupplierDto();
            suppDto.setId(entity.getSupplier().getId());
            suppDto.setSupplierName(entity.getSupplier().getSupplierName());
            suppDto.setGstNumber(entity.getSupplier().getGstNumber());
            suppDto.setPhone(entity.getSupplier().getPhone());
            dto.setSupplier(suppDto);
        }

        dto.setPurchaseDate(entity.getPurchaseDate());
        dto.setPaymentTerms(entity.getPaymentTerms());
        dto.setTotalTaxableAmount(entity.getTotalTaxableAmount());
        dto.setTotalCgst(entity.getTotalCgst());
        dto.setTotalSgst(entity.getTotalSgst());
        dto.setTotalIgst(entity.getTotalIgst());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setPaidAmount(entity.getPaidAmount());
        dto.setPaymentStatus(entity.getPaymentStatus());
        dto.setStatus(entity.getStatus());
        dto.setNotes(entity.getNotes());

        if (entity.getItems() != null) {
            dto.setItems(entity.getItems().stream().map(it -> {
                PurchaseInvoiceItemDto itemDto = new PurchaseInvoiceItemDto();
                itemDto.setId(it.getId());
                itemDto.setItemVariantId(it.getItemVariant() != null ? it.getItemVariant().getId() : null);
                itemDto.setItemName(it.getItemName());
                itemDto.setHsnCode(it.getHsnCode());
                itemDto.setBatchNumber(it.getBatchNumber());
                itemDto.setExpiryDate(it.getExpiryDate());
                itemDto.setQuantity(it.getQuantity());
                itemDto.setUnitCost(it.getUnitCost());
                itemDto.setDiscount(it.getDiscount());
                itemDto.setTaxableAmount(it.getTaxableAmount());
                itemDto.setGstRate(it.getGstRate());
                itemDto.setCgstAmount(it.getCgstAmount());
                itemDto.setSgstAmount(it.getSgstAmount());
                itemDto.setIgstAmount(it.getIgstAmount());
                itemDto.setLineTotal(it.getLineTotal());
                return itemDto;
            }).toList());
        }

        return dto;
    }
}
