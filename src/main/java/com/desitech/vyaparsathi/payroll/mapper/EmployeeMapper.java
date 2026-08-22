package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.EmployeeDto;
import com.desitech.vyaparsathi.payroll.entity.Employee;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public EmployeeDto toDto(Employee entity) {
        if (entity == null) {
            return null;
        }

        return EmployeeDto.builder()
                .id(entity.getId())
                .employeeCode(entity.getEmployeeCode())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .gender(entity.getGender())
                .dateOfBirth(entity.getDateOfBirth())
                .joiningDate(entity.getJoiningDate())
                .exitDate(entity.getExitDate())
                .employmentStatus(entity.getEmploymentStatus())
                .employmentType(entity.getEmploymentType())
                .department(entity.getDepartment())
                .designation(entity.getDesignation())
                .reportingTo(entity.getReportingTo())
                .bankAccountNumber(entity.getBankAccountNumber())
                .bankIFSCCode(entity.getBankIFSCCode())
                .bankName(entity.getBankName())
                .bankBranch(entity.getBankBranch())
                .bankBeneficiaryName(entity.getBankBeneficiaryName())
                .upiId(entity.getUpiId())
                .paymentPreference(entity.getPaymentPreference())
                .panNumber(entity.getPanNumber())
                .aadhaarNumber(entity.getAadhaarNumber())
                .uanNumber(entity.getUanNumber())
                .pfEnrolled(entity.getPfEnrolled())
                .esicNumber(entity.getEsicNumber())
                .esicEnrolled(entity.getEsicEnrolled())
                .ptState(entity.getPtState())
                .taxRegime(entity.getTaxRegime())
                .salaryStructureId(entity.getSalaryStructureId())
                .monthlyCTC(entity.getMonthlyCTC())
                .isActive(entity.getIsActive())
                .build();
    }

    public Employee toEntity(EmployeeDto dto) {
        if (dto == null) {
            return null;
        }

        Employee entity = new Employee();
        entity.setId(dto.getId());
        entity.setEmployeeCode(dto.getEmployeeCode());
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        entity.setGender(dto.getGender());
        entity.setDateOfBirth(dto.getDateOfBirth());
        entity.setJoiningDate(dto.getJoiningDate());
        entity.setExitDate(dto.getExitDate());
        entity.setEmploymentStatus(dto.getEmploymentStatus());
        entity.setEmploymentType(dto.getEmploymentType());
        entity.setDepartment(dto.getDepartment());
        entity.setDesignation(dto.getDesignation());
        entity.setReportingTo(dto.getReportingTo());
        entity.setBankAccountNumber(dto.getBankAccountNumber());
        entity.setBankIFSCCode(dto.getBankIFSCCode());
        entity.setBankName(dto.getBankName());
        entity.setBankBranch(dto.getBankBranch());
        entity.setBankBeneficiaryName(dto.getBankBeneficiaryName());
        entity.setUpiId(dto.getUpiId());
        entity.setPaymentPreference(dto.getPaymentPreference());
        entity.setPanNumber(dto.getPanNumber());
        entity.setAadhaarNumber(dto.getAadhaarNumber());
        entity.setUanNumber(dto.getUanNumber());
        entity.setPfEnrolled(dto.getPfEnrolled());
        entity.setEsicNumber(dto.getEsicNumber());
        entity.setEsicEnrolled(dto.getEsicEnrolled());
        entity.setPtState(dto.getPtState());
        entity.setTaxRegime(dto.getTaxRegime());
        entity.setSalaryStructureId(dto.getSalaryStructureId());
        entity.setMonthlyCTC(dto.getMonthlyCTC());
        entity.setIsActive(dto.getIsActive());
        return entity;
    }

    public void updateEntityFromDto(EmployeeDto dto, Employee entity) {
        if (dto == null || entity == null) {
            return;
        }

        entity.setEmployeeCode(dto.getEmployeeCode());
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        entity.setGender(dto.getGender());
        entity.setDateOfBirth(dto.getDateOfBirth());
        entity.setJoiningDate(dto.getJoiningDate());
        entity.setExitDate(dto.getExitDate());
        entity.setEmploymentStatus(dto.getEmploymentStatus());
        entity.setEmploymentType(dto.getEmploymentType());
        entity.setDepartment(dto.getDepartment());
        entity.setDesignation(dto.getDesignation());
        entity.setReportingTo(dto.getReportingTo());
        entity.setBankAccountNumber(dto.getBankAccountNumber());
        entity.setBankIFSCCode(dto.getBankIFSCCode());
        entity.setBankName(dto.getBankName());
        entity.setBankBranch(dto.getBankBranch());
        entity.setBankBeneficiaryName(dto.getBankBeneficiaryName());
        entity.setUpiId(dto.getUpiId());
        entity.setPaymentPreference(dto.getPaymentPreference());
        entity.setPanNumber(dto.getPanNumber());
        entity.setAadhaarNumber(dto.getAadhaarNumber());
        entity.setUanNumber(dto.getUanNumber());
        entity.setPfEnrolled(dto.getPfEnrolled());
        entity.setEsicNumber(dto.getEsicNumber());
        entity.setEsicEnrolled(dto.getEsicEnrolled());
        entity.setPtState(dto.getPtState());
        entity.setTaxRegime(dto.getTaxRegime());
        entity.setSalaryStructureId(dto.getSalaryStructureId());
        entity.setMonthlyCTC(dto.getMonthlyCTC());
        entity.setIsActive(dto.getIsActive());
    }
}
