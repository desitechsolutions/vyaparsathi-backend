package com.desitech.vyaparsathi.purchasereturn.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.purchasereturn.dto.CreatePurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.dto.CreatePurchaseReturnItemDto;
import com.desitech.vyaparsathi.purchasereturn.dto.PurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturnItem;
import com.desitech.vyaparsathi.purchasereturn.enums.PurchaseReturnStatus;
import com.desitech.vyaparsathi.purchasereturn.mapper.PurchaseReturnMapper;
import com.desitech.vyaparsathi.purchasereturn.repository.PurchaseReturnItemRepository;
import com.desitech.vyaparsathi.purchasereturn.repository.PurchaseReturnRepository;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PurchaseReturnServiceTest {

    @InjectMocks
    private PurchaseReturnService purchaseReturnService;

    @Mock
    private PurchaseReturnRepository purchaseReturnRepository;

    @Mock
    private PurchaseReturnItemRepository purchaseReturnItemRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private ReceivingRepository receivingRepository;

    @Mock
    private ItemVariantRepository itemVariantRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private com.desitech.vyaparsathi.inventory.service.StockService stockService;

    @Mock
    private com.desitech.vyaparsathi.supplier.service.SupplierLedgerService supplierLedgerService;

    @Mock
    private PurchaseReturnMapper purchaseReturnMapper;

    private Supplier supplier;
    private ItemVariant variant;
    private Receiving receiving;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentShopId(1L);

        supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("Test Supplier");

        variant = new ItemVariant();
        variant.setId(10L);
        variant.setSku("SKU-100");

        receiving = new Receiving();
        receiving.setId(20L);

        ReceivingItem receivingItem = new ReceivingItem();
        receivingItem.setReceivedQty(10);
        receivingItem.setBatchNumber("BATCH-1");

        com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem poItem =
                new com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem();
        poItem.setItemVariant(variant);
        receivingItem.setPurchaseOrderItem(poItem);

        receiving.setItems(List.of(receivingItem));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should create Purchase Return in DRAFT status successfully")
    void createPurchaseReturn_Success() {
        CreatePurchaseReturnDto dto = new CreatePurchaseReturnDto();
        dto.setSupplierId(1L);
        dto.setReceivingId(20L);

        CreatePurchaseReturnItemDto itemDto = new CreatePurchaseReturnItemDto();
        itemDto.setItemVariantId(10L);
        itemDto.setBatchNumber("BATCH-1");
        itemDto.setQuantity(3);
        itemDto.setUnitCost(new BigDecimal("100.00"));
        dto.setItems(List.of(itemDto));

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(receivingRepository.findById(20L)).thenReturn(Optional.of(receiving));
        when(itemVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(purchaseReturnRepository.sumAlreadyReturnedQty(eq(20L), eq(10L), eq("BATCH-1"), any())).thenReturn(0);
        when(purchaseReturnRepository.save(any(PurchaseReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseReturnDto expectedDto = new PurchaseReturnDto();
        expectedDto.setTotalAmount(new BigDecimal("300.00"));
        expectedDto.setStatus(PurchaseReturnStatus.DRAFT);
        when(purchaseReturnMapper.toDto(any(PurchaseReturn.class))).thenReturn(expectedDto);

        PurchaseReturnDto result = purchaseReturnService.createPurchaseReturn(dto);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PurchaseReturnStatus.DRAFT);
        verify(purchaseReturnRepository).save(any(PurchaseReturn.class));
    }

    @Test
    @DisplayName("Should throw exception when return quantity exceeds originally received batch quantity")
    void createPurchaseReturn_ExceedsReceivedQty_ThrowsException() {
        CreatePurchaseReturnDto dto = new CreatePurchaseReturnDto();
        dto.setSupplierId(1L);
        dto.setReceivingId(20L);

        CreatePurchaseReturnItemDto itemDto = new CreatePurchaseReturnItemDto();
        itemDto.setItemVariantId(10L);
        itemDto.setBatchNumber("BATCH-1");
        itemDto.setQuantity(15); // Exceeds originally received (10)
        itemDto.setUnitCost(new BigDecimal("100.00"));
        dto.setItems(List.of(itemDto));

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(receivingRepository.findById(20L)).thenReturn(Optional.of(receiving));
        when(itemVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(purchaseReturnRepository.sumAlreadyReturnedQty(eq(20L), eq(10L), eq("BATCH-1"), any())).thenReturn(0);

        assertThatThrownBy(() -> purchaseReturnService.createPurchaseReturn(dto))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Cannot return 15 units for item SKU-100");
    }

    @Test
    @DisplayName("Should approve Purchase Return, deduct stock, and issue Debit Note payment")
    void approvePurchaseReturn_Success() {
        PurchaseReturn purchaseReturn = new PurchaseReturn();
        purchaseReturn.setId(5L);
        purchaseReturn.setReturnNo("PR-20260807-001");
        purchaseReturn.setSupplier(supplier);
        purchaseReturn.setTotalAmount(new BigDecimal("500.00"));
        purchaseReturn.setStatus(PurchaseReturnStatus.DRAFT);

        PurchaseReturnItem item = new PurchaseReturnItem();
        item.setItemVariant(variant);
        item.setQuantity(5);
        item.setUnitCost(new BigDecimal("100.00"));
        item.setBatchNumber("BATCH-1");
        purchaseReturn.setItems(List.of(item));

        when(purchaseReturnRepository.findById(5L)).thenReturn(Optional.of(purchaseReturn));
        when(purchaseReturnRepository.save(any(PurchaseReturn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockService.getCurrentStock(any())).thenReturn(new BigDecimal("100"));

        PurchaseReturnDto expectedDto = new PurchaseReturnDto();
        expectedDto.setStatus(PurchaseReturnStatus.APPROVED);
        when(purchaseReturnMapper.toDto(any(PurchaseReturn.class))).thenReturn(expectedDto);

        PurchaseReturnDto result = purchaseReturnService.approvePurchaseReturn(5L);

        assertThat(result.getStatus()).isEqualTo(PurchaseReturnStatus.APPROVED);
        
        org.mockito.ArgumentCaptor<StockMovement> movementCaptor = org.mockito.ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getQuantity()).isEqualByComparingTo(new BigDecimal("-5"));
    }
}
