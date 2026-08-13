package com.desitech.vyaparsathi.einvoice.provider;

import com.desitech.vyaparsathi.einvoice.dto.EWayBillRequestDto;
import com.desitech.vyaparsathi.sales.entity.Sale;

import java.time.LocalDateTime;

/**
 * Synthetic E-Way Bill provider — generates a plausible-looking 12-digit
 * number from the current millisecond clock. Used in dev / staging and while
 * real GSP credentials are pending.
 *
 * The generated number is NOT a real NIC-issued EWB and will not validate
 * against the government portal. Do NOT enable this provider in production
 * for GST-liable shipments — switch to {@link NicGspEWayBillProvider}.
 *
 * Validity days: request.distanceKm / 100, floored at 1 day. Mirrors the
 * NIC rule where 100 km of distance grants 1 day of validity.
 */
public class MockEWayBillProvider implements EWayBillProvider {

    @Override
    public Result generate(Sale sale, EWayBillRequestDto request) {
        String ewayBillNo = "331" + (System.currentTimeMillis() % 1000000000L);
        LocalDateTime now = LocalDateTime.now();
        int distanceKm = request.getDistanceKm() != null ? request.getDistanceKm() : 100;
        int daysValid = Math.max(1, distanceKm / 100);
        LocalDateTime validUntil = now.plusDays(daysValid);
        return new Result(ewayBillNo, now, validUntil);
    }

    @Override
    public String getProviderName() {
        return "mock";
    }
}
