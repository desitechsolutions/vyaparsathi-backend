package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.InsufficientStockException;
import com.desitech.vyaparsathi.inventory.dto.StockTransferCreateDto;
import com.desitech.vyaparsathi.inventory.dto.StockTransferDto;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.entity.StockTransfer;
import com.desitech.vyaparsathi.inventory.entity.StockTransferItem;
import com.desitech.vyaparsathi.inventory.enums.StockTransferStatus;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockTransferRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockTransferServiceTest {

    @Mock
    private StockTransferRepository transferRepository;
    @Mock
    private ItemVariantRepository variantRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private StockService stockService;

    @InjectMocks
    private StockTransferService stockTransferService;

    private Shop shopA;
    private Shop shopB;
    private ItemVariant variant;

    @BeforeEach
    void setUp() {
        shopA = new Shop();
        shopA.setId(1L);
        shopA.setName("Main Store");

        shopB = new Shop();
        shopB.setId(2L);
        shopB.setName("Branch Store");

        variant = new ItemVariant();
        variant.setId(10L);
        variant.setSku("SKU-100");
    }

    @Test
    @DisplayName("Should create transfer when request is valid")
    void createTransfer_success() {
        StockTransferCreateDto req = new StockTransferCreateDto();
        req.setFromShopId(1L);
        req.setToShopId(2L);
        req.setNotes("Inter-branch shift");

        StockTransferCreateDto.StockTransferLineDto line = new StockTransferCreateDto.StockTransferLineDto();
        line.setItemVariantId(10L);
        line.setQuantity(new BigDecimal("5.00"));
        line.setBatchNumber("BATCH-A");
        req.setItems(List.of(line));

        when(shopRepository.findById(1L)).thenReturn(Optional.of(shopA));
        when(shopRepository.findById(2L)).thenReturn(Optional.of(shopB));
        when(variantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto result = stockTransferService.createTransfer(req);

        assertNotNull(result);
        assertEquals(1L, result.getFromShopId());
        assertEquals(2L, result.getToShopId());
        assertEquals(StockTransferStatus.PENDING, result.getStatus());
        assertEquals(1, result.getItems().size());
        assertEquals(new BigDecimal("5.00"), result.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("Should throw exception when source and destination shops are the same")
    void createTransfer_sameShop_throwsException() {
        StockTransferCreateDto req = new StockTransferCreateDto();
        req.setFromShopId(1L);
        req.setToShopId(1L);

        StockTransferCreateDto.StockTransferLineDto line = new StockTransferCreateDto.StockTransferLineDto();
        line.setItemVariantId(10L);
        line.setQuantity(new BigDecimal("5.00"));
        req.setItems(List.of(line));

        assertThrows(BusinessValidationException.class, () -> stockTransferService.createTransfer(req));
    }

    @Test
    @DisplayName("Should execute transfer and record stock movement when available stock is sufficient")
    void executeTransfer_success() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(100L);
        transfer.setTransferNumber("TRF-20250101-00001");
        transfer.setFromShop(shopA);
        transfer.setToShop(shopB);
        transfer.setStatus(StockTransferStatus.PENDING);

        StockTransferItem item = new StockTransferItem();
        item.setStockTransfer(transfer);
        item.setItemVariant(variant);
        item.setQuantity(new BigDecimal("10.00"));
        item.setBatchNumber("B123");
        transfer.getItems().add(item);

        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(stockService.getCurrentStock(10L)).thenReturn(new BigDecimal("50.00"));
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto result = stockTransferService.executeTransfer(100L);

        assertEquals(StockTransferStatus.COMPLETED, result.getStatus());
        verify(stockService).deductStock(eq(10L), eq(new BigDecimal("10.00")), anyString(), anyString());
        verify(stockService).addStockFromDto(any());
    }

    @Test
    @DisplayName("Should throw InsufficientStockException when source shop does not have enough stock")
    void executeTransfer_insufficientStock_throwsException() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(100L);
        transfer.setTransferNumber("TRF-20250101-00001");
        transfer.setFromShop(shopA);
        transfer.setToShop(shopB);
        transfer.setStatus(StockTransferStatus.PENDING);

        StockTransferItem item = new StockTransferItem();
        item.setStockTransfer(transfer);
        item.setItemVariant(variant);
        item.setQuantity(new BigDecimal("100.00"));
        transfer.getItems().add(item);

        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(stockService.getCurrentStock(10L)).thenReturn(new BigDecimal("5.00"));

        assertThrows(InsufficientStockException.class, () -> stockTransferService.executeTransfer(100L));
    }

    @Test
    @DisplayName("Should cancel transfer when status is PENDING")
    void cancelTransfer_success() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(100L);
        transfer.setFromShop(shopA);
        transfer.setToShop(shopB);
        transfer.setStatus(StockTransferStatus.PENDING);

        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto result = stockTransferService.cancelTransfer(100L);

        assertEquals(StockTransferStatus.CANCELLED, result.getStatus());
    }
}
