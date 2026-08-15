package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.receiving.entity.AdvanceShipmentNotice;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.repository.AdvanceShipmentNoticeRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CRUD + pre-fill for supplier ASNs. When a GRN is created against a PO that
 * has a matching pending ASN, {@link #consumeForReceiving} stamps the ASN and
 * copies its header hints (carrier, vehicle, tracking, expected arrival) onto
 * the GRN so the operator doesn't retype them.
 */
@Service
public class AdvanceShipmentNoticeService {

    private final AdvanceShipmentNoticeRepository asnRepository;
    private final ReceivingRepository receivingRepository;
    private final ShopRepository shopRepository;

    public AdvanceShipmentNoticeService(AdvanceShipmentNoticeRepository asnRepository,
                                        ReceivingRepository receivingRepository,
                                        ShopRepository shopRepository) {
        this.asnRepository = asnRepository;
        this.receivingRepository = receivingRepository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public AdvanceShipmentNotice create(AdvanceShipmentNotice payload) {
        if (payload.getShop() == null) {
            Long shopId = TenantUtils.getCurrentShopId();
            if (shopId != null) {
                Shop shop = shopRepository.findById(shopId).orElse(null);
                payload.setShop(shop);
            }
        }
        payload.setCreatedAt(LocalDateTime.now());
        if (payload.getStatus() == null) payload.setStatus("PENDING");
        return asnRepository.save(payload);
    }

    @Transactional(readOnly = true)
    public List<AdvanceShipmentNotice> listByPurchaseOrder(Long poId) {
        return asnRepository.findByPurchaseOrderIdOrderByDispatchDateDesc(poId);
    }

    @Transactional(readOnly = true)
    public AdvanceShipmentNotice get(Long id) {
        return asnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ASN not found: " + id));
    }

    /**
     * Consumes an ASN for a specific GRN — copies header hints onto the GRN
     * and marks the ASN as CONSUMED so it's not reused. No-op if the ASN is
     * already consumed by a different GRN.
     */
    @Transactional
    public AdvanceShipmentNotice consumeForReceiving(Long asnId, Long receivingId) {
        AdvanceShipmentNotice asn = get(asnId);
        Receiving r = receivingRepository.findById(receivingId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found: " + receivingId));

        if (asn.getConsumedReceivingId() != null
                && !asn.getConsumedReceivingId().equals(receivingId)) {
            throw new com.desitech.vyaparsathi.common.exception.BusinessValidationException(
                    "ASN " + asn.getAsnNumber() + " is already consumed by GRN " + asn.getConsumedReceivingId());
        }

        if (asn.getVehicleNo() != null && r.getVehicleNo() == null) r.setVehicleNo(asn.getVehicleNo());
        if (asn.getExpectedArrival() != null && r.getExpectedDeliveryDate() == null) {
            r.setExpectedDeliveryDate(asn.getExpectedArrival());
        }
        if (asn.getTrackingNumber() != null && r.getDeliveryChallanNo() == null) {
            r.setDeliveryChallanNo(asn.getTrackingNumber());
        }
        receivingRepository.save(r);

        asn.setConsumedReceivingId(receivingId);
        asn.setStatus("CONSUMED");
        return asnRepository.save(asn);
    }
}
