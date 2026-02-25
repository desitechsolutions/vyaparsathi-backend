package com.desitech.vyaparsathi.subscriptions.service;

import com.desitech.vyaparsathi.subscriptions.dto.PricingPlanDTO;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.mapper.PricingPlanMapper;
import com.desitech.vyaparsathi.subscriptions.repository.PricingPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingPlanService {

    private final PricingPlanRepository repository;
    private final PricingPlanMapper mapper;

    /**
     * Fetch all active plans for the public pricing page.
     */
    @Transactional(readOnly = true)
    public List<PricingPlanDTO> getActivePlans() {
        log.info("Fetching all active pricing plans");
        List<PricingPlanConfig> plans = repository.findAllActiveWithFeatures();
        return mapper.toDtoList(plans);
    }

    /**
     * Get a specific plan config (used for internal pro-rata calculations).
     */
    @Transactional(readOnly = true)
    public PricingPlanConfig getPlanConfig(Tier tier) {
        return repository.findById(tier)
                .orElseThrow(() -> new RuntimeException("Pricing tier configuration not found for: " + tier));
    }

    /**
     * Admin functionality: Update or Create a plan configuration.
     */
    @Transactional
    public PricingPlanDTO saveOrUpdatePlan(PricingPlanDTO dto) {
        log.info("Updating configuration for tier: {}", dto.getTier());

        PricingPlanConfig entity = repository.findById(dto.getTier())
                .orElse(new PricingPlanConfig());

        // MapStruct updates the existing entity with new DTO values
        mapper.updateEntityFromDto(dto, entity);

        // Ensure the ID is set correctly if it was a new plan
        entity.setTier(dto.getTier());

        PricingPlanConfig saved = repository.save(entity);
        return mapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public PricingPlanDTO getPlanByTier(Tier tier) {
        log.info("Fetching details for tier: {}", tier);
        PricingPlanConfig entity = repository.findByTierWithFeatures(tier)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + tier));
        return mapper.toDto(entity);
    }
}