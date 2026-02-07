package com.desitech.vyaparsathi.inventory.mapper;

import com.desitech.vyaparsathi.inventory.dto.CategoryCreateDto;
import com.desitech.vyaparsathi.inventory.dto.CategoryDto;
import com.desitech.vyaparsathi.inventory.dto.CategoryUpdateDto;
import com.desitech.vyaparsathi.inventory.entity.Category;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoryMapper {

    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "parentName", source = "parent.name")
    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "children", source = "children")
    CategoryDto toDto(Category entity);

    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "children", ignore = true)
    Category toEntity(CategoryCreateDto dto);

    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "id", ignore = true)
    void updateFromDto(CategoryUpdateDto dto, @MappingTarget Category entity);

    @Mapping(target = "parent", ignore = true)      // Usually already set
    @Mapping(target = "shop", ignore = true)        // Usually already set
    @Mapping(target = "children", ignore = true)    // Avoid recursion
    @Mapping(target = "id", source = "id")          // Keep the existing ID
    Category toEntity(CategoryDto dto);
}