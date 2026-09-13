package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.gst.dto.HsnMasterDto;
import com.desitech.vyaparsathi.gst.entity.HsnMaster;
import com.desitech.vyaparsathi.gst.repository.HsnMasterRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HsnMasterService {

    private final HsnMasterRepository hsnRepo;

    public HsnMasterService(HsnMasterRepository hsnRepo) {
        this.hsnRepo = hsnRepo;
    }

    @Transactional(readOnly = true)
    public List<HsnMasterDto> search(String q) {
        if (q == null || q.isBlank()) return List.of();
        String trimmed = q.trim();
        String codePfx, descLike;
        if (trimmed.matches("[0-9]+.*")) {
            codePfx  = trimmed + "%";
            descLike = "%" + trimmed + "%";
        } else {
            codePfx  = trimmed + "%";
            descLike = "%" + trimmed + "%";
        }
        return hsnRepo.search(codePfx, descLike, PageRequest.of(0, 20))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private HsnMasterDto toDto(HsnMaster h) {
        HsnMasterDto dto = new HsnMasterDto();
        dto.setId(h.getId());
        dto.setHsnCode(h.getHsnCode());
        dto.setDescription(h.getDescription());
        dto.setGstType(h.getGstType());
        dto.setDefaultRate(h.getDefaultRate());
        dto.setDefaultUqc(h.getDefaultUqc());
        return dto;
    }
}
