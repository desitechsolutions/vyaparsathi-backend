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
    private List<ItemVariantDto> variants;
}
