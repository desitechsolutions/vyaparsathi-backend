package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "item", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_item_name_brand_shop",
                columnNames = {"name", "brand_name", "shop_id"}
        )
})
@Getter
@Setter
@NoArgsConstructor
public class Item extends ShopAwareEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "brand_name")
    private String brandName;

    @Column
    private String fabric;

    @Column
    private String season;

    @Column(name = "attribute_1")
    private String attribute1;

    @Column(name = "attribute_2")
    private String attribute2;

    /**
     * Product specifications, ingredients, or key attributes.
     * Examples: "100% Cotton", "SKD Kit - 24 pieces".
     */
    @Column(name = "specifications", length = 500)
    private String specifications;

    // A Item can have many variants (e.g., T-Shirt can be size M, L)
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ItemVariant> variants;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }

    public String getFabric() { return fabric; }
    public void setFabric(String fabric) { this.fabric = fabric; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getAttribute1() { return attribute1; }
    public void setAttribute1(String attribute1) { this.attribute1 = attribute1; }

    public String getAttribute2() { return attribute2; }
    public void setAttribute2(String attribute2) { this.attribute2 = attribute2; }

    public String getSpecifications() { return specifications; }
    public void setSpecifications(String specifications) { this.specifications = specifications; }

    public List<ItemVariant> getVariants() { return variants; }
    public void setVariants(List<ItemVariant> variants) { this.variants = variants; }
}