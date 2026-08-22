package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class BarcodeLabelServiceTest {

    private ItemVariantRepository itemVariantRepository;
    private BarcodeLabelService labelService;

    @BeforeEach
    void setUp() {
        itemVariantRepository = Mockito.mock(ItemVariantRepository.class);
        labelService = new BarcodeLabelService(itemVariantRepository);
    }

    private ItemVariant createMockVariant(Long id, String sku, String barcode, String name, BigDecimal mrp) {
        Item item = new Item();
        item.setName(name);

        ItemVariant v = new ItemVariant();
        v.setId(id);
        v.setSku(sku);
        v.setBarcode(barcode);
        v.setItem(item);
        v.setMrp(mrp);
        return v;
    }

    @Test
    void testRenderSingleLabel() {
        ItemVariant v = createMockVariant(1L, "SKU-001", "8901234567890", "Test Product", new BigDecimal("499.00"));
        when(itemVariantRepository.findById(1L)).thenReturn(Optional.of(v));

        byte[] pdf = labelService.renderLabels(List.of(1L), 1);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0, "PDF bytes should be generated for single item");
    }

    @Test
    void testRenderTwoLabels() {
        ItemVariant v1 = createMockVariant(1L, "SKU-001", "8901234567890", "Item 1", new BigDecimal("100.00"));
        ItemVariant v2 = createMockVariant(2L, "SKU-002", "8901234567891", "Item 2", new BigDecimal("200.00"));
        when(itemVariantRepository.findById(1L)).thenReturn(Optional.of(v1));
        when(itemVariantRepository.findById(2L)).thenReturn(Optional.of(v2));

        byte[] pdf = labelService.renderLabels(List.of(1L, 2L), 1);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0, "PDF bytes should be generated for two items");
    }

    @Test
    void testRenderMultipleCopies() {
        ItemVariant v = createMockVariant(1L, "SKU-001", "8901234567890", "Test Product", new BigDecimal("499.00"));
        when(itemVariantRepository.findById(1L)).thenReturn(Optional.of(v));

        byte[] pdf = labelService.renderLabels(List.of(1L), 5);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0, "PDF bytes should be generated for 5 copies");
    }

    @Test
    void testRenderEmptyList() {
        byte[] pdf = labelService.renderLabels(List.of(), 1);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0, "PDF bytes should be generated even for empty list without throwing exception");
    }
}
