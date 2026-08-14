package com.desitech.vyaparsathi.shop.customfield.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.shop.customfield.dto.ShopCustomAttributeDefDto;
import com.desitech.vyaparsathi.shop.customfield.entity.ShopCustomAttributeDef;
import com.desitech.vyaparsathi.shop.customfield.repository.ShopCustomAttributeDefRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRUD + reorder for per-shop custom attribute definitions. Every
 * mutation scopes to the current shop via {@link TenantUtils}, so a
 * shop owner cannot see or touch another tenant's definitions.
 */
@Service
public class ShopCustomAttributeDefService {

    @Autowired
    private ShopCustomAttributeDefRepository repository;

    public List<ShopCustomAttributeDefDto> listForCurrentShop() {
        Long shopId = TenantUtils.getCurrentShopId();
        return repository.findAllActiveForShop(shopId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ShopCustomAttributeDefDto create(ShopCustomAttributeDefDto dto) {
        Long shopId = TenantUtils.getCurrentShopId();
        assertFieldTypeValid(dto);
        if (repository.existsByShopIdAndKeyName(shopId, dto.getKeyName())) {
            throw new BusinessValidationException(
                    "A custom field with key \"" + dto.getKeyName() + "\" already exists for this shop.");
        }
        ShopCustomAttributeDef entity = new ShopCustomAttributeDef();
        applyDto(entity, dto);
        // Auto-assign displayOrder to end of list if the caller didn't set one.
        if (entity.getDisplayOrder() == null) {
            int max = repository.findAllActiveForShop(shopId).stream()
                    .mapToInt(d -> d.getDisplayOrder() == null ? 0 : d.getDisplayOrder())
                    .max().orElse(-1);
            entity.setDisplayOrder(max + 1);
        }
        return toDto(repository.save(entity));
    }

    @Transactional
    public ShopCustomAttributeDefDto update(Long id, ShopCustomAttributeDefDto dto) {
        Long shopId = TenantUtils.getCurrentShopId();
        ShopCustomAttributeDef entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custom attribute not found: " + id));
        assertOwnedBy(entity, shopId);
        assertFieldTypeValid(dto);
        // Key changes are allowed but must remain unique per shop.
        if (dto.getKeyName() != null && !dto.getKeyName().equals(entity.getKeyName())
                && repository.existsByShopIdAndKeyName(shopId, dto.getKeyName())) {
            throw new BusinessValidationException(
                    "A custom field with key \"" + dto.getKeyName() + "\" already exists for this shop.");
        }
        applyDto(entity, dto);
        return toDto(repository.save(entity));
    }

    /**
     * Soft-delete — sets {@code active = false} so historical
     * item_variant.custom_attributes JSON blobs still make sense to
     * anyone auditing old sales.
     */
    @Transactional
    public void delete(Long id) {
        Long shopId = TenantUtils.getCurrentShopId();
        ShopCustomAttributeDef entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custom attribute not found: " + id));
        assertOwnedBy(entity, shopId);
        entity.setActive(Boolean.FALSE);
        repository.save(entity);
    }

    /**
     * Bulk reorder given an ordered list of IDs. Any ID that doesn't
     * belong to this shop is silently skipped — never fails a whole
     * reorder because one row was renamed under the user's feet.
     */
    @Transactional
    public List<ShopCustomAttributeDefDto> reorder(List<Long> orderedIds) {
        Long shopId = TenantUtils.getCurrentShopId();
        List<ShopCustomAttributeDef> current = repository.findAllActiveForShop(shopId);
        Map<Long, ShopCustomAttributeDef> byId = new HashMap<>();
        for (ShopCustomAttributeDef d : current) byId.put(d.getId(), d);

        int order = 0;
        List<ShopCustomAttributeDef> toSave = new ArrayList<>();
        for (Long id : orderedIds) {
            ShopCustomAttributeDef d = byId.remove(id);
            if (d == null) continue;
            d.setDisplayOrder(order++);
            toSave.add(d);
        }
        // Anything not in the ordered list keeps a stable relative order at the end.
        for (ShopCustomAttributeDef d : byId.values()) {
            d.setDisplayOrder(order++);
            toSave.add(d);
        }
        repository.saveAll(toSave);
        return listForCurrentShop();
    }

    // ── helpers ────────────────────────────────────────────

    private void assertOwnedBy(ShopCustomAttributeDef entity, Long shopId) {
        if (entity.getShop() == null || !shopId.equals(entity.getShop().getId())) {
            throw new EntityNotFoundException("Custom attribute not found: " + entity.getId());
        }
    }

    private void assertFieldTypeValid(ShopCustomAttributeDefDto dto) {
        if (dto.getFieldType() == null
                || !ShopCustomAttributeDefDto.ALLOWED_FIELD_TYPES.contains(dto.getFieldType())) {
            throw new BusinessValidationException(
                    "Unsupported field type: " + dto.getFieldType() +
                    ". Allowed: " + ShopCustomAttributeDefDto.ALLOWED_FIELD_TYPES);
        }
        if ("select".equals(dto.getFieldType())
                && (dto.getOptions() == null || dto.getOptions().isEmpty())) {
            throw new BusinessValidationException("Select fields must specify at least one option.");
        }
    }

    private void applyDto(ShopCustomAttributeDef entity, ShopCustomAttributeDefDto dto) {
        if (dto.getKeyName() != null)      entity.setKeyName(dto.getKeyName());
        if (dto.getLabel() != null)        entity.setLabel(dto.getLabel());
        if (dto.getFieldType() != null)    entity.setFieldType(dto.getFieldType());
        if (dto.getRequired() != null)     entity.setRequired(dto.getRequired());
        if (dto.getOptions() != null)      entity.setOptions(dto.getOptions());
        if (dto.getHelpText() != null)     entity.setHelpText(dto.getHelpText());
        if (dto.getDisplayOrder() != null) entity.setDisplayOrder(dto.getDisplayOrder());
        if (dto.getActive() != null)       entity.setActive(dto.getActive());
    }

    private ShopCustomAttributeDefDto toDto(ShopCustomAttributeDef d) {
        return ShopCustomAttributeDefDto.builder()
                .id(d.getId())
                .keyName(d.getKeyName())
                .label(d.getLabel())
                .fieldType(d.getFieldType())
                .required(d.getRequired())
                .options(d.getOptions())
                .helpText(d.getHelpText())
                .displayOrder(d.getDisplayOrder())
                .active(d.getActive())
                .build();
    }
}
