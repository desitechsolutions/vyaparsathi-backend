package com.desitech.vyaparsathi.platform.service;

import com.desitech.vyaparsathi.platform.dto.PlatformDetailsDto;
import com.desitech.vyaparsathi.platform.entity.PlatformDetails;
import com.desitech.vyaparsathi.platform.repository.PlatformDetailsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformDetailsService {

    private final PlatformDetailsRepository platformDetailsRepository;

    /**
     * Fetch current active platform details configuration, creating default row if none exists.
     */
    @Transactional
    public PlatformDetails getPlatformDetailsEntity() {
        return platformDetailsRepository.findTopByOrderByIdAsc().orElseGet(() -> {
            log.info("[PLATFORM] Seeding default PlatformDetails entity.");
            PlatformDetails seed = PlatformDetails.builder()
                    .companyName("DesiTech Solutions Pvt. Ltd.")
                    .tradeName("VyaparSathi Enterprise SaaS")
                    .gstin("27AAACD1234E1Z5")
                    .pan("AAACD1234E")
                    .addressLine1("101, Tech Hub Tower")
                    .addressLine2("Senapati Bapat Marg, Lower Parel")
                    .city("Mumbai")
                    .state("Maharashtra")
                    .stateCode("27")
                    .pincode("400013")
                    .supportEmail("support@desitechsolutions.in")
                    .supportPhone("+91 98765 43210")
                    .hsnSacCode("998313")
                    .invoicePrefix("SUB-INV")
                    .bankName("HDFC Bank Ltd.")
                    .accountNumber("50200012345678")
                    .ifscCode("HDFC0000123")
                    .upiId("vyaparsathi@hdfcbank")
                    .build();
            return platformDetailsRepository.save(seed);
        });
    }

    @Transactional(readOnly = true)
    public PlatformDetailsDto getPlatformDetails() {
        PlatformDetails entity = getPlatformDetailsEntity();
        return convertToDto(entity);
    }

    /**
     * Update platform details (SuperAdmin restricted).
     */
    @Transactional
    public PlatformDetailsDto updatePlatformDetails(PlatformDetailsDto dto) {
        PlatformDetails entity = getPlatformDetailsEntity();

        entity.setCompanyName(dto.getCompanyName());
        entity.setTradeName(dto.getTradeName());
        entity.setGstin(dto.getGstin());
        entity.setPan(dto.getPan());
        entity.setAddressLine1(dto.getAddressLine1());
        entity.setAddressLine2(dto.getAddressLine2());
        entity.setCity(dto.getCity());
        entity.setState(dto.getState());
        entity.setStateCode(dto.getStateCode());
        entity.setPincode(dto.getPincode());
        entity.setSupportEmail(dto.getSupportEmail());
        entity.setSupportPhone(dto.getSupportPhone());
        entity.setHsnSacCode(dto.getHsnSacCode() != null && !dto.getHsnSacCode().isBlank() ? dto.getHsnSacCode() : "998313");
        entity.setInvoicePrefix(dto.getInvoicePrefix() != null && !dto.getInvoicePrefix().isBlank() ? dto.getInvoicePrefix() : "SUB-INV");
        entity.setBankName(dto.getBankName());
        entity.setAccountNumber(dto.getAccountNumber());
        entity.setIfscCode(dto.getIfscCode());
        entity.setUpiId(dto.getUpiId());

        PlatformDetails saved = platformDetailsRepository.save(entity);
        log.info("[PLATFORM] PlatformDetails configuration updated successfully by SuperAdmin.");
        return convertToDto(saved);
    }

    private PlatformDetailsDto convertToDto(PlatformDetails entity) {
        return PlatformDetailsDto.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .tradeName(entity.getTradeName())
                .gstin(entity.getGstin())
                .pan(entity.getPan())
                .addressLine1(entity.getAddressLine1())
                .addressLine2(entity.getAddressLine2())
                .city(entity.getCity())
                .state(entity.getState())
                .stateCode(entity.getStateCode())
                .pincode(entity.getPincode())
                .supportEmail(entity.getSupportEmail())
                .supportPhone(entity.getSupportPhone())
                .hsnSacCode(entity.getHsnSacCode())
                .invoicePrefix(entity.getInvoicePrefix())
                .bankName(entity.getBankName())
                .accountNumber(entity.getAccountNumber())
                .ifscCode(entity.getIfscCode())
                .upiId(entity.getUpiId())
                .build();
    }
}
