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

    // A Item can have many variants (e.g., T-Shirt can be size M, L)
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ItemVariant> variants;

}