package com.desitech.vyaparsathi.sales.dto;

import com.desitech.vyaparsathi.common.annotations.AuditValue;
import com.desitech.vyaparsathi.common.util.CustomLocalDateTimeDeserializer;
import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.delivery.dto.DeliveryDTO;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SaleDto {
    private Long id;
    private CustomerDto customer;
    private List<SaleItemDto> items;
    private List<PaymentDto> paymentDetails;
    private DeliveryDTO delivery;

    /*@AuditValue*/
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private BigDecimal roundOff;
    private BigDecimal discount;
    private Boolean isGstRequired;

    @AuditValue
    private String invoiceNo;


    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime date;

    private String signedInvoiceUrl;
    private String status;

    // --- Pharmacy-specific fields ---
    /** Name of the prescribing doctor (required for Schedule H1 and X drugs). */
    private String doctorName;
    /** Patient name if different from the customer. */
    private String patientName;
    /** Prescription / Rx number for pharmacy compliance tracking. */
    private String prescriptionNumber;
    /** Registration number of the prescribing doctor (required for Schedule H1 / X compliance). */
    private String doctorRegistrationNumber;

    private java.time.LocalDate dueDate;
    private BigDecimal invoiceDiscount;
    private BigDecimal shippingCharges;
    private BigDecimal otherCharges;
}
