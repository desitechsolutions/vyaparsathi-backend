package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.inventory.dto.LowStockAlertDto;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.notification.service.EmailService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LowStockAlertNotificationSchedulerTest {

    @Mock private ShopRepository shopRepository;
    @Mock private StockService stockService;
    @Mock private ItemVariantRepository itemVariantRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private LowStockAlertNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testNotifyShopWithCriticalAndLowStockAlerts() throws Exception {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("Test Kirana Store");
        shop.setEmail("owner@testkirana.com");
        shop.setActive(true);
        shop.setLowStockAlertsEnabled(true);

        LowStockAlertDto outOfStock = new LowStockAlertDto();
        outOfStock.setItemVariantId(101L);
        outOfStock.setItemName("Tata Salt 1kg");
        outOfStock.setSku("SALT-TATA-1KG");
        outOfStock.setCurrentStock(BigDecimal.ZERO);
        outOfStock.setThreshold(new BigDecimal("10"));
        outOfStock.setUnit("PCS");
        outOfStock.setAlertLevel("CRITICAL");
        outOfStock.setLastPurchasePrice(new BigDecimal("25.00"));
        outOfStock.setSuggestedOrderQty(new BigDecimal("20"));

        LowStockAlertDto lowStock = new LowStockAlertDto();
        lowStock.setItemVariantId(102L);
        lowStock.setItemName("Fortune Oil 1L");
        lowStock.setSku("OIL-FORTUNE-1L");
        lowStock.setCurrentStock(new BigDecimal("3"));
        lowStock.setThreshold(new BigDecimal("15"));
        lowStock.setUnit("PCS");
        lowStock.setAlertLevel("LOW");
        lowStock.setLastPurchasePrice(new BigDecimal("130.00"));
        lowStock.setSuggestedOrderQty(new BigDecimal("12"));

        when(stockService.getLowStockAlerts()).thenReturn(List.of(outOfStock, lowStock));
        when(itemVariantRepository.findById(anyLong())).thenReturn(Optional.of(new ItemVariant()));
        when(itemVariantRepository.findAllById(anyList())).thenReturn(List.of(new ItemVariant(), new ItemVariant()));

        boolean notified = scheduler.notifyShop(shop);

        assertTrue(notified, "Shop should be notified");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendEmail(eq("owner@testkirana.com"), subjectCaptor.capture(), bodyCaptor.capture());

        String subject = subjectCaptor.getValue();
        assertTrue(subject.contains("Test Kirana Store"), "Subject should contain shop name");
        assertTrue(subject.contains("2 low-stock alerts"), "Subject should contain total alert count");
        assertTrue(subject.contains("1 out of stock"), "Subject should mention out of stock count");

        String body = bodyCaptor.getValue();
        assertTrue(body.contains("Tata Salt 1kg"));
        assertTrue(body.contains("Fortune Oil 1L"));
        assertTrue(body.contains("Out of Stock"), "Body should contain Out of Stock badge");
        assertTrue(body.contains("Low Stock"), "Body should contain Low Stock badge");
    }

    @Test
    void testSkipWhenLowStockAlertsDisabled() {
        Shop shop = new Shop();
        shop.setId(2L);
        shop.setActive(true);
        shop.setLowStockAlertsEnabled(false);
        shop.setEmail("disabled@shop.com");

        when(shopRepository.findAll()).thenReturn(List.of(shop));

        scheduler.sendDailyDigest();

        verifyNoInteractions(emailService);
    }
}
