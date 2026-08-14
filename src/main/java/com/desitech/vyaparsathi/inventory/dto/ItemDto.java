package com.desitech.vyaparsathi.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ItemDto {
    private Long id;

    @NotBlank(message = "Item name is required")
    @Size(max = 200, message = "Item name must be 200 characters or fewer")
    private String name;

    @Size(max = 1000, message = "Description must be 1000 characters or fewer")
    private String description;

    @NotNull(message = "Category is required")
    private Long categoryId;

    private String categoryName;
    private String brandName;

    /**
     * Generic attribute slot #1 — industry-specific label
     * (Fabric for CLOTHING, Material for HARDWARE, Metal for JEWELLERY,
     * Packaging for GROCERY, etc.). Backed by the {@code attribute_1}
     * column on {@code item}. The legacy {@code fabric} / {@code season}
     * columns were dropped in migration V76.
     */
    private String attribute1;

    private String attribute2;

    /** Product specifications, ingredients, or key attributes (renamed from composition). */
    @Size(max = 500)
    private String specifications;

    @Valid
    private List<ItemVariantDto> variants;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }

    public String getAttribute1() { return attribute1; }
    public void setAttribute1(String attribute1) { this.attribute1 = attribute1; }

    public String getAttribute2() { return attribute2; }
    public void setAttribute2(String attribute2) { this.attribute2 = attribute2; }

    public String getSpecifications() { return specifications; }
    public void setSpecifications(String specifications) { this.specifications = specifications; }

    public List<ItemVariantDto> getVariants() { return variants; }
    public void setVariants(List<ItemVariantDto> variants) { this.variants = variants; }
}
