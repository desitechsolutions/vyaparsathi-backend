package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.Form16Dto;
import com.desitech.vyaparsathi.payroll.entity.Form16Data;
import org.springframework.stereotype.Component;

@Component
public class Form16Mapper {
    public Form16Dto toDto(Form16Data entity) {
        if (entity == null) return null;
        Form16Dto dto = new Form16Dto();
        dto.setId(entity.getId());
        dto.setFinancialYear(entity.getFinancialYear());
        dto.setPanNumber(entity.getPanNumber());
        dto.setName(entity.getName());
        dto.setTotalSalary(entity.getTotalSalary());
        dto.setGrossTotalIncome(entity.getGrossTotalIncome());
        dto.setPfContribution(entity.getPfContribution());
        dto.setTdsPayable(entity.getTdsPayable());
        dto.setTaxPaidThisYear(entity.getTaxPaidThisYear());
        dto.setIsVerified(entity.getIsVerified());
        dto.setPdfPath(entity.getPdfPath());
        return dto;
    }

    public Form16Data toEntity(Form16Dto dto) {
        if (dto == null) return null;
        Form16Data entity = new Form16Data();
        entity.setFinancialYear(dto.getFinancialYear());
        entity.setTotalSalary(dto.getTotalSalary());
        entity.setGrossTotalIncome(dto.getGrossTotalIncome());
        return entity;
    }
}
