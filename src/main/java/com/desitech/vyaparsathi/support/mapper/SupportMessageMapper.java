package com.desitech.vyaparsathi.support.mapper;

import com.desitech.vyaparsathi.support.dto.SupportMessage;
import com.desitech.vyaparsathi.support.entity.ChatMessageEntity;
import com.desitech.vyaparsathi.shop.entity.Shop;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SupportMessageMapper {

    // =========================
    // ENTITY → DTO
    // =========================

    @Mapping(source = "shop.id", target = "shopId")
    @Mapping(source = "shop.name", target = "shopName")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "createdAt", target = "timestamp") // or message time if different
    SupportMessage toDto(ChatMessageEntity entity);


    // =========================
    // DTO → ENTITY
    // =========================

    //@Mapping(target = "shop", source = "shopId", qualifiedByName = "shopFromId")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ChatMessageEntity toEntity(SupportMessage dto);


    // =========================
    // CUSTOM SHOP MAPPER
    // =========================

/*    @Named("shopFromId")
    default Shop shopFromId(Long shopId) {
        if (shopId == null) return null;
        Shop shop = new Shop();
        shop.setId(shopId);
        return shop;
    }*/
}
