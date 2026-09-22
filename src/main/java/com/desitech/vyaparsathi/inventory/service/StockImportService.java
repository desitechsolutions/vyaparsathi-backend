package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.inventory.dto.StockImportResultDto;
import com.desitech.vyaparsathi.inventory.dto.StockImportRowPreviewDto;
import com.desitech.vyaparsathi.inventory.dto.StockImportValidationDto;
import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
import com.desitech.vyaparsathi.inventory.repository.ItemRepository;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Handles bulk stock import via Excel files (batch-tracking retail: FMCG, food, cosmetics).
 * <p>
 * Each row in the spreadsheet represents one batch entry. If the Item does not exist
 * it is created automatically. If the ItemVariant (identified by SKU) already exists
 * its selling-price / MRP / pack-size fields are updated to the values in the file.
 * A fresh ADD stock movement is created for each non-zero quantity row.
 * </p>
 */
@Service
public class StockImportService {

    private static final Logger logger = LoggerFactory.getLogger(StockImportService.class);

    // Column indices in the import template (0-based)
    static final int COL_ITEM_NAME        = 0;
    static final int COL_SKU              = 1;
    static final int COL_UNIT             = 2;
    static final int COL_CATEGORY         = 3;
    static final int COL_HSN              = 4;
    static final int COL_GST_RATE         = 5;
    static final int COL_BATCH_NUMBER     = 6;
    static final int COL_MFG_DATE         = 7;
    static final int COL_EXPIRY_DATE      = 8;
    static final int COL_QUANTITY         = 9;
    static final int COL_COST_PER_UNIT    = 10;
    static final int COL_SELLING_PRICE    = 11;
    static final int COL_MRP              = 12;

    /** Column headers used for both template generation and import validation. */
    static final String[] HEADERS = {
            "Item Name*", "SKU", "Unit*", "Category",
            "HSN Code", "GST Rate (%)", "Batch Number",
            "Manufacturing Date (YYYY-MM-DD)", "Expiry Date (YYYY-MM-DD)",
            "Quantity*", "Cost Per Unit", "Selling Price*",
            "MRP"
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemVariantRepository itemVariantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    // -------------------------------------------------------------------------
    // Template download
    // -------------------------------------------------------------------------

    /**
     * Generates a blank Excel workbook containing the import template with
     * column headers and one sample row so users know what to fill in.
     */
    public byte[] generateImportTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Stock Import");

            // Header row
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }

            // Sample row — generic retail item so the template works for any industry type
            Row sample = sheet.createRow(1);
            sample.createCell(COL_ITEM_NAME).setCellValue("Sample Product");
            sample.createCell(COL_SKU).setCellValue("SKU-SAMPLE-001");
            sample.createCell(COL_UNIT).setCellValue("pcs");
            sample.createCell(COL_CATEGORY).setCellValue("General");
            sample.createCell(COL_HSN).setCellValue("84713010");
            sample.createCell(COL_GST_RATE).setCellValue(18);
            sample.createCell(COL_BATCH_NUMBER).setCellValue("BATCH-001");
            sample.createCell(COL_MFG_DATE).setCellValue("2024-01-01");
            sample.createCell(COL_EXPIRY_DATE).setCellValue("");
            sample.createCell(COL_QUANTITY).setCellValue(50);
            sample.createCell(COL_COST_PER_UNIT).setCellValue(100.00);
            sample.createCell(COL_SELLING_PRICE).setCellValue(150.00);
            sample.createCell(COL_MRP).setCellValue(175.00);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // -------------------------------------------------------------------------
    // Import processing
    // -------------------------------------------------------------------------

    /**
     * Parses the uploaded Excel file and creates/updates inventory records.
     * Each row is processed independently; errors on one row do not abort the rest.
     * <p>
     * Items are loaded upfront in a single query to avoid N+1 issues.
     * Batch-specific data (batch number, expiry date) is stored only in the
     * {@code StockMovement} record – it is NOT written back to {@code ItemVariant}
     * metadata here because the import may contain multiple batches for the same
     * variant with different expiry dates.
     * </p>
     *
     * @param file the uploaded .xlsx workbook
     * @return summary with counts and per-row error messages
     */
    /**
     * Validates an uploaded Excel file without committing any database modifications.
     * Evaluates required fields, checks for duplicate SKUs both within the uploaded file
     * and against the current shop's existing inventory, and returns a detailed preview.
     */
    @Transactional(readOnly = true)
    public StockImportValidationDto validateExcel(MultipartFile file) throws IOException {
        Long shopId = TenantContext.getCurrentShopId();
        StockImportValidationDto validation = new StockImportValidationDto();

        List<StockImportRowPreviewDto> rows = new ArrayList<>();
        Set<String> seenSkusInFile = new HashSet<>();
        Set<String> duplicateSkusInFile = new HashSet<>();
        List<String> allFileSkus = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            // Pass 1: Collect non-empty SKUs and find duplicates within the file
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;
                String rawSku = getStringCell(row, COL_SKU);
                if (rawSku != null && !rawSku.trim().isBlank()) {
                    String cleanSku = rawSku.trim();
                    allFileSkus.add(cleanSku);
                    if (!seenSkusInFile.add(cleanSku.toLowerCase())) {
                        duplicateSkusInFile.add(cleanSku.toLowerCase());
                    }
                }
            }

            // Find existing SKUs in the current shop from DB in a single batch query
            Set<String> existingDbSkus = new HashSet<>();
            if (!allFileSkus.isEmpty()) {
                List<String> dbMatches = itemVariantRepository.findExistingSkusInShop(shopId, allFileSkus);
                if (dbMatches != null) {
                    for (String s : dbMatches) {
                        existingDbSkus.add(s.trim().toLowerCase());
                    }
                }
            }

            // Pass 2: Validate each row and construct preview
            int total = 0;
            int duplicates = 0;
            int errors = 0;

            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;
                total++;

                int userRow = r + 1;
                String itemName = getStringCell(row, COL_ITEM_NAME);
                String rawSku = getStringCell(row, COL_SKU);
                String sku = rawSku != null ? rawSku.trim() : "";
                String unit = getStringCell(row, COL_UNIT);
                BigDecimal quantity = getBigDecimalCell(row, COL_QUANTITY);
                BigDecimal sellingPrice = getBigDecimalCell(row, COL_SELLING_PRICE);
                String batchNumber = getStringCell(row, COL_BATCH_NUMBER);
                LocalDate expiryDate = getDateCell(row, COL_EXPIRY_DATE);

                StringBuilder rowErr = new StringBuilder();
                if (itemName == null || itemName.isBlank()) rowErr.append("'Item Name' is required. ");
                if (unit == null || unit.isBlank()) rowErr.append("'Unit' is required. ");
                if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) rowErr.append("'Quantity' must be > 0. ");
                if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) < 0) rowErr.append("'Selling Price' must be >= 0. ");

                boolean isDbDup = !sku.isBlank() && existingDbSkus.contains(sku.toLowerCase());
                boolean isFileDup = !sku.isBlank() && duplicateSkusInFile.contains(sku.toLowerCase());
                boolean isDup = isDbDup || isFileDup;

                String dupReason = null;
                if (isDbDup && isFileDup) {
                    dupReason = "SKU exists in shop inventory AND repeated in file";
                } else if (isDbDup) {
                    dupReason = "SKU already exists in your shop inventory";
                } else if (isFileDup) {
                    dupReason = "SKU repeated across multiple rows in file";
                }

                String status = "VALID";
                if (rowErr.length() > 0) {
                    status = "ERROR";
                    errors++;
                } else if (isDup) {
                    status = "DUPLICATE";
                    duplicates++;
                }

                StockImportRowPreviewDto rowPreview = StockImportRowPreviewDto.builder()
                        .rowNumber(userRow)
                        .itemName(itemName)
                        .sku(sku)
                        .unit(unit)
                        .sellingPrice(sellingPrice)
                        .quantity(quantity)
                        .batchNumber(batchNumber)
                        .expiryDate(expiryDate != null ? expiryDate.format(DATE_FMT) : null)
                        .duplicate(isDup)
                        .duplicateReason(dupReason)
                        .status(status)
                        .errorMessage(rowErr.length() > 0 ? rowErr.toString().trim() : null)
                        .build();

                rows.add(rowPreview);
            }

            validation.setTotalRows(total);
            validation.setDuplicateCount(duplicates);
            validation.setErrorCount(errors);
            validation.setValidRows(total - errors);
            validation.setHasDuplicates(duplicates > 0);
            validation.setRows(rows);
        }

        return validation;
    }

    /**
     * Parses the uploaded Excel file and creates/updates inventory records.
     * Backwards-compatible overload defaulting to skipDuplicates = false.
     */
    public StockImportResultDto importFromExcel(MultipartFile file) throws IOException {
        return importFromExcel(file, false);
    }

    /**
     * Parses the uploaded Excel file and creates/updates inventory records.
     * Each row is processed in its own transaction via transactionTemplate so errors
     * on one row do not abort the rest and do not cause UnexpectedRollbackException.
     *
     * @param file the uploaded .xlsx workbook
     * @param skipDuplicates if true, rows matching an existing SKU in this shop are skipped
     * @return summary with counts and per-row error/warning messages
     */
    public StockImportResultDto importFromExcel(MultipartFile file, boolean skipDuplicates) throws IOException {
        StockImportResultDto result = new StockImportResultDto();

        // Pre-load all existing items into a map (itemName -> Item) to avoid N+1 queries
        Map<String, Item> itemCache = new HashMap<>();
        itemRepository.findAll().forEach(i -> itemCache.put(i.getName().toLowerCase(), i));

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            // Skip header row (row 0), start from row 1
            for (int rowIdx = 1; rowIdx <= lastRow; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                result.setTotalRows(result.getTotalRows() + 1);
                final int userRowNumber = rowIdx + 1;
                try {
                    Boolean processed = transactionTemplate.execute(status -> {
                        return processRow(row, userRowNumber, itemCache, skipDuplicates, result);
                    });

                    if (Boolean.TRUE.equals(processed)) {
                        result.setSuccessCount(result.getSuccessCount() + 1);
                    }
                } catch (Exception e) {
                    result.setErrorCount(result.getErrorCount() + 1);
                    result.getErrors().add("Row " + userRowNumber + ": " + e.getMessage());
                    logger.warn("Import error on row {}: {}", userRowNumber, e.getMessage());
                }
            }
        }

        logger.info("Stock import complete – total={}, success={}, skipped={}, errors={}",
                result.getTotalRows(), result.getSuccessCount(), result.getSkippedCount(), result.getErrorCount());
        return result;
    }

    // -------------------------------------------------------------------------
    // Row processing
    // -------------------------------------------------------------------------

    private boolean processRow(Row row, int userRowNumber, Map<String, Item> itemCache,
                               boolean skipDuplicates, StockImportResultDto result) {
        // --- Required fields ---
        String itemName = getStringCell(row, COL_ITEM_NAME);
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("'Item Name' is required");
        }

        String unit = getStringCell(row, COL_UNIT);
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("'Unit' is required");
        }

        BigDecimal quantity = getBigDecimalCell(row, COL_QUANTITY);
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("'Quantity' must be greater than zero");
        }

        BigDecimal sellingPrice = getBigDecimalCell(row, COL_SELLING_PRICE);
        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("'Selling Price' is required and must be non-negative");
        }

        // --- Optional fields ---
        String rawSku        = getStringCell(row, COL_SKU);
        String sku           = rawSku != null ? rawSku.trim() : null;
        String categoryName  = getStringCell(row, COL_CATEGORY);
        String hsn           = getStringCell(row, COL_HSN);
        Integer gstRate      = getIntegerCell(row, COL_GST_RATE);
        String batchNumber   = getStringCell(row, COL_BATCH_NUMBER);
        LocalDate mfgDate    = getDateCell(row, COL_MFG_DATE);
        LocalDate expiryDate = getDateCell(row, COL_EXPIRY_DATE);
        BigDecimal cost      = getBigDecimalCell(row, COL_COST_PER_UNIT);
        BigDecimal mrp       = getBigDecimalCell(row, COL_MRP);

        Long shopId = TenantContext.getCurrentShopId();

        // Check duplicate SKU behavior
        if (sku != null && !sku.isBlank()) {
            Optional<ItemVariant> existing = itemVariantRepository.findByShopIdAndSku(shopId, sku);
            if (existing.isPresent()) {
                if (skipDuplicates) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getWarnings().add("Row " + userRowNumber + ": SKU '" + sku + "' already exists in inventory — skipped.");
                    return false; // row skipped
                }
            }
        }

        // --- Find or create Item (uses cache to avoid N+1) ---
        Item item = findOrCreateItem(itemName, categoryName, itemCache);

        // --- Find or create ItemVariant ---
        ItemVariant variant = findOrCreateVariant(item, sku, unit, sellingPrice, hsn, gstRate, mrp, shopId);

        // --- Add stock movement ---
        BigDecimal costPerUnit = (cost != null && cost.compareTo(BigDecimal.ZERO) > 0) ? cost : BigDecimal.ZERO;

        StockMovement movement = new StockMovement();
        movement.setItemVariant(variant);
        movement.setMovementType(StockMovementType.ADD);
        movement.setQuantity(quantity);
        movement.setCostPerUnit(costPerUnit);
        movement.setBatch(batchNumber);
        movement.setExpiryDate(expiryDate);
        movement.setReason("Excel Import");
        movement.setReference("Import");
        movement.setTimestamp(LocalDateTime.now());
        stockMovementRepository.save(movement);
        return true;
    }

    private Item findOrCreateItem(String itemName, String categoryName, Map<String, Item> itemCache) {
        String key = itemName.toLowerCase();
        Item cached = itemCache.get(key);
        if (cached != null) {
            return cached;
        }

        // Create new item
        Item item = new Item();
        item.setName(itemName);

        if (categoryName != null && !categoryName.isBlank()) {
            Long shopId = TenantContext.getCurrentShopId();
            Optional<Category> category = categoryRepository.findByNameAndShopId(categoryName, shopId);
            category.ifPresent(item::setCategory);
        }

        Item saved = itemRepository.save(item);
        itemCache.put(key, saved);
        return saved;
    }

    private ItemVariant findOrCreateVariant(Item item, String sku, String unit,
                                             BigDecimal sellingPrice, String hsn, Integer gstRate,
                                             BigDecimal mrp, Long shopId) {
        // If SKU provided and variant already exists for this shop, update and return
        if (sku != null && !sku.isBlank()) {
            Optional<ItemVariant> existing = itemVariantRepository.findByShopIdAndSku(shopId, sku);
            if (existing.isPresent()) {
                ItemVariant v = existing.get();
                v.setPricePerUnit(sellingPrice);
                if (mrp != null) v.setMrp(mrp);
                if (gstRate != null) v.setGstRate(gstRate);
                return itemVariantRepository.save(v);
            }
        }

        // Create new variant
        ItemVariant variant = new ItemVariant();
        variant.setItem(item);
        variant.setSku(sku != null && !sku.isBlank() ? sku : generateSku());
        variant.setUnit(unit);
        variant.setPricePerUnit(sellingPrice);
        if (hsn != null && !hsn.isBlank()) variant.setHsn(hsn);
        if (gstRate != null) variant.setGstRate(gstRate);
        if (mrp != null) variant.setMrp(mrp);

        return itemVariantRepository.save(variant);
    }

    private String generateSku() {
        Long shopId = TenantContext.getCurrentShopId();
        String shopPrefix = shopId != null ? shopId.toString() : "0";
        // Use current time millis to ensure uniqueness across imports and tenants
        return "IMP-" + shopPrefix + "-" + System.currentTimeMillis();
    }

    // -------------------------------------------------------------------------
    // Cell reading helpers
    // -------------------------------------------------------------------------

    private boolean isRowEmpty(Row row) {
        for (int i = COL_ITEM_NAME; i <= COL_MRP; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellAsString(cell);
                if (val != null && !val.isBlank()) return false;
            }
        }
        return true;
    }

    private String getStringCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        return getCellAsString(cell);
    }

    private String getCellAsString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> null;
        };
    }

    private BigDecimal getBigDecimalCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String s = getCellAsString(cell);
            return (s == null || s.isBlank()) ? null : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getIntegerCell(Row row, int colIdx) {
        BigDecimal bd = getBigDecimalCell(row, colIdx);
        return bd != null ? bd.intValue() : null;
    }

    private LocalDate getDateCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            java.util.Date d = cell.getDateCellValue();
            return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }

        String s = getCellAsString(cell);
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format '" + s + "' – expected YYYY-MM-DD");
        }
    }

}
