package com.desitech.vyaparsathi.common.entities;

import com.desitech.vyaparsathi.common.listener.ShopEntityListener;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@MappedSuperclass
@EntityListeners(ShopEntityListener.class)
@Filter(name = "shopFilter", condition = "shop_id = :shopId")
@Getter
@Setter
public abstract class ShopAwareEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }
}
