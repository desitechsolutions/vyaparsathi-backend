package com.desitech.vyaparsathi.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** One entry in the "Reference Documents" block. */
@Getter
@Setter
@NoArgsConstructor
public class DocumentReferenceDto {
    /** PO / GRN / TAX_INVOICE / DELIVERY_CHALLAN / DEBIT_NOTE / CREDIT_NOTE etc. */
    private String type;
    private Long id;
    private String number;
    private LocalDate date;

    public DocumentReferenceDto(String type, Long id, String number, LocalDate date) {
        this.type = type;
        this.id = id;
        this.number = number;
        this.date = date;
    }
}
