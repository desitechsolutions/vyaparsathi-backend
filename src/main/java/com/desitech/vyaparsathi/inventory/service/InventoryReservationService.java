package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.entity.InventoryReservation;
import com.desitech.vyaparsathi.inventory.repository.InventoryReservationRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Soft-hold service. Reservations reduce the sellable pool without emitting
 * stock movements. When a downstream document (sales order, quotation)
 * finalizes it should call {@link #consume(Long)} so the reservation
 * disappears and the normal stock deduct takes over. If it cancels or expires,
 * {@link #release(Long)} returns the qty to the pool. A scheduled sweep
 * expires anything past its {@code expires_at} timestamp.
 */
@Service
public class InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

    private final InventoryReservationRepository reservationRepository;
    private final ShopRepository shopRepository;

    public InventoryReservationService(InventoryReservationRepository reservationRepository,
                                   ShopRepository shopRepository) {
        this.reservationRepository = reservationRepository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public InventoryReservation reserve(Long itemVariantId, BigDecimal qty, String reason,
                                    String referenceType, Long referenceId,
                                    String reservedBy, LocalDateTime expiresAt) {
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessValidationException("Reservation quantity must be positive.");
        }
        InventoryReservation r = new InventoryReservation();
        r.setItemVariantId(itemVariantId);
        r.setQuantity(qty);
        r.setReason(reason != null ? reason : "MANUAL_HOLD");
        r.setReferenceType(referenceType);
        r.setReferenceId(referenceId);
        r.setReservedBy(reservedBy);
        r.setReservedAt(LocalDateTime.now());
        r.setExpiresAt(expiresAt);
        r.setStatus("ACTIVE");
        Long shopId = TenantUtils.getCurrentShopId();
        if (shopId != null) {
            Shop shop = shopRepository.findById(shopId).orElse(null);
            r.setShop(shop);
        }
        return reservationRepository.save(r);
    }

    @Transactional
    public InventoryReservation consume(Long reservationId) {
        InventoryReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        if (!"ACTIVE".equals(r.getStatus())) {
            throw new BusinessValidationException("Reservation is already " + r.getStatus());
        }
        r.setStatus("CONSUMED");
        r.setReleasedAt(LocalDateTime.now());
        return reservationRepository.save(r);
    }

    @Transactional
    public InventoryReservation release(Long reservationId) {
        InventoryReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        if (!"ACTIVE".equals(r.getStatus())) return r;
        r.setStatus("RELEASED");
        r.setReleasedAt(LocalDateTime.now());
        return reservationRepository.save(r);
    }

    @Transactional(readOnly = true)
    public BigDecimal reservedFor(Long itemVariantId) {
        BigDecimal amount = reservationRepository.sumActiveReservations(itemVariantId);
        return amount != null ? amount : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public List<InventoryReservation> listActive(Long itemVariantId) {
        return reservationRepository.findByItemVariantIdAndStatus(itemVariantId, "ACTIVE");
    }

    /** Sweep expired reservations every 10 minutes. */
    @Scheduled(fixedDelayString = "${app.inventory.reservation.sweep-ms:600000}")
    @Transactional
    public void expireStale() {
        List<InventoryReservation> stale = reservationRepository
                .findByStatusAndExpiresAtBefore("ACTIVE", LocalDateTime.now());
        int expired = 0;
        for (InventoryReservation r : stale) {
            r.setStatus("EXPIRED");
            r.setReleasedAt(LocalDateTime.now());
            reservationRepository.save(r);
            expired++;
        }
        if (expired > 0) log.info("Expired {} stale stock reservations", expired);
    }
}
