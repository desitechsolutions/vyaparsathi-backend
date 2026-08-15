package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.inventory.entity.UomConversion;
import com.desitech.vyaparsathi.inventory.repository.UomConversionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Simple UOM conversion. Two-step lookup: exact match on from→to first, then
 * inverse (to→from) with a reciprocal factor. Missing conversions throw so
 * callers see the gap rather than silently using 1.0.
 */
@Service
public class UomConversionService {

    private final UomConversionRepository repository;

    public UomConversionService(UomConversionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UomConversion save(UomConversion payload) {
        if (payload.getFromUnit() == null || payload.getToUnit() == null || payload.getFactor() == null) {
            throw new BusinessValidationException("fromUnit, toUnit and factor are required.");
        }
        payload.setFromUnit(payload.getFromUnit().toUpperCase());
        payload.setToUnit(payload.getToUnit().toUpperCase());
        return repository.save(payload);
    }

    @Transactional(readOnly = true)
    public List<UomConversion> listAll() {
        return repository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit) {
        if (fromUnit == null || toUnit == null || fromUnit.equalsIgnoreCase(toUnit)) return quantity;
        UomConversion forward = repository.findByFromUnitAndToUnitAndActiveTrue(
                fromUnit.toUpperCase(), toUnit.toUpperCase()).orElse(null);
        if (forward != null) return quantity.multiply(forward.getFactor());
        UomConversion inverse = repository.findByFromUnitAndToUnitAndActiveTrue(
                toUnit.toUpperCase(), fromUnit.toUpperCase()).orElse(null);
        if (inverse != null && inverse.getFactor().signum() > 0) {
            return quantity.divide(inverse.getFactor(), 6, RoundingMode.HALF_UP);
        }
        throw new BusinessValidationException("No conversion defined for " + fromUnit + " → " + toUnit);
    }
}
