package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.TaxDeclarationDto;
import com.desitech.vyaparsathi.payroll.entity.TaxDeclaration;
import org.springframework.stereotype.Component;

@Component
public class TaxDeclarationMapper {
    public TaxDeclarationDto toDto(TaxDeclaration entity) {
        if (entity == null) return null;
        TaxDeclarationDto dto = new TaxDeclarationDto();
        dto.setId(entity.getId());
        dto.setFinancialYear(entity.getFinancialYear());
        dto.setTaxRegime(entity.getTaxRegime());
        dto.setLifeInsurancePremium(entity.getLifeInsurancePremium());
        dto.setMedicalInsurancePremium(entity.getMedicalInsurancePremium());
        dto.setEducationExpenses(entity.getEducationExpenses());
        dto.setNpsContribution(entity.getNpsContribution());
        dto.setTotalTaxableIncome(entity.getTotalTaxableIncome());
        return dto;
    }

    public TaxDeclaration toEntity(TaxDeclarationDto dto) {
        if (dto == null) return null;
        TaxDeclaration entity = new TaxDeclaration();
        entity.setFinancialYear(dto.getFinancialYear());
        entity.setTaxRegime(dto.getTaxRegime());
        entity.setLifeInsurancePremium(dto.getLifeInsurancePremium());
        entity.setMedicalInsurancePremium(dto.getMedicalInsurancePremium());
        entity.setEducationExpenses(dto.getEducationExpenses());
        entity.setNpsContribution(dto.getNpsContribution());
        return entity;
    }
}
