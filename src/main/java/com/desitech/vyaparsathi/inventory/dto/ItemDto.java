package com.desitech.vyaparsathi.inventory.dto;

import com.desitech.vyaparsathi.inventory.enums.DrugSchedule;
import lombok.Data;
import java.util.List;

@Data
public class ItemDto {
    private Long id;
    private String name;
    private String description;
    private Long categoryId;
    private String categoryName;
    private String brandName;
    private String fabric;
    private String season;
    private String attribute1;
    private String attribute2;

    // --- Pharmacy-specific fields ---
    /** Drug schedule classification (PHARMACY shops). */
    private DrugSchedule drugSchedule;
    /** Whether a valid prescription is required to sell this medicine. */
    private Boolean requiresPrescription;
    /** Active pharmaceutical ingredient(s) and strength (e.g., "Paracetamol 500mg"). */
    private String composition;
    /** Storage requirement (e.g., "Refrigerated 2–8°C", "Room Temperature"). */
    private String storageRequirement;

    private List<ItemVariantDto> variants;
}
