package com.desitech.vyaparsathi.inventory.dto;

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
    private List<ItemVariantDto> variants;
}
