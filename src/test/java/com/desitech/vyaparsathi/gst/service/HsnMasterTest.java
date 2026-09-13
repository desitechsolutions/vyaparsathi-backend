package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.gst.dto.HsnMasterDto;
import com.desitech.vyaparsathi.gst.entity.HsnMaster;
import com.desitech.vyaparsathi.gst.repository.HsnMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HsnMasterTest {

    @Mock private HsnMasterRepository hsnRepo;
    @InjectMocks private HsnMasterService hsnService;

    private HsnMaster hsn(String code, String desc, double rate) {
        HsnMaster h = new HsnMaster();
        h.setHsnCode(code);
        h.setDescription(desc);
        h.setGstType("GOODS");
        h.setDefaultRate(BigDecimal.valueOf(rate));
        h.setDefaultUqc("PCS");
        return h;
    }

    @Test
    void search_byCodePrefix_returnsMatchingEntries() {
        HsnMaster tshirts = hsn("6109", "T-shirts, singlets and other vests", 12);
        when(hsnRepo.search(eq("6109%"), any(), any(PageRequest.class)))
                .thenReturn(List.of(tshirts));

        List<HsnMasterDto> results = hsnService.search("6109");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getHsnCode()).isEqualTo("6109");
        assertThat(results.get(0).getDefaultRate()).isEqualByComparingTo("12");
    }

    @Test
    void search_byDescriptionKeyword_returnsMatchingEntries() {
        HsnMaster soap = hsn("3401", "Soap, organic surface-active products", 18);
        when(hsnRepo.search(any(), eq("%soap%"), any(PageRequest.class)))
                .thenReturn(List.of(soap));

        List<HsnMasterDto> results = hsnService.search("soap");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDescription()).containsIgnoringCase("soap");
    }
}
