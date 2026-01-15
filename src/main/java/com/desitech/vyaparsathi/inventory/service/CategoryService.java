package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.inventory.dto.CategoryCreateDto;
import com.desitech.vyaparsathi.inventory.dto.CategoryDto;
import com.desitech.vyaparsathi.inventory.dto.CategoryUpdateDto;
import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.mapper.CategoryMapper;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
import com.desitech.vyaparsathi.inventory.repository.ItemRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private CategoryMapper categoryMapper;
    @Autowired private ShopRepository shopRepository;

    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toDto)
                .collect(Collectors.toList());
    }

    public CategoryDto getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .map(categoryMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));
    }

    @Transactional
    public CategoryDto createCategory(CategoryCreateDto dto) {
        Category category = categoryMapper.toEntity(dto);

        // Set shop from tenant context
        Shop shop = shopRepository.findById(TenantContext.getCurrentShopId())
                .orElseThrow(() -> new EntityNotFoundException("Shop not found"));
        category.setShop(shop);

        // Set parent if provided
        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent category not found"));
            category.setParent(parent);
        }

        Category saved = categoryRepository.save(category);
        return categoryMapper.toDto(saved);
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryUpdateDto dto) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));

        categoryMapper.updateFromDto(dto, existing);

        // Update parent if changed
        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent not found"));
            existing.setParent(parent);
        } else {
            existing.setParent(null); // Make root
        }

        Category updated = categoryRepository.save(existing);
        return categoryMapper.toDto(updated);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new EntityNotFoundException("Category not found with id: " + id);
        }

        if (itemRepository.existsByCategoryId(id)) {
            throw new DataIntegrityViolationException("Cannot delete category: It is in use by one or more items.");
        }

        categoryRepository.deleteById(id);
    }
}