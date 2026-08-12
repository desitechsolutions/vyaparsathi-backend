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

    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private BigDecimal roundOff;
    private BigDecimal discount;
    private Boolean isGstRequired;

    /** Reverse-charge outward supply. Recipient is liable to pay tax; PDF shows the notice; GSTR-1 rchrg="Y". */
    private Boolean reverseCharge;

    /**
     * Client-supplied idempotency key for POST /api/sales. Same key + same
     * shop within retention window returns the original sale instead of a
     * duplicate — safe to retry on network hiccups or POS double-tap.
     */
    private String idempotencyKey;

    /** Optional user id of the salesperson responsible for this sale. */
    private Long salespersonId;

    /** Free-text notes on the sale. */
    private String notes;

    @AuditValue
    private String invoiceNo;

    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime date;

    private String signedInvoiceUrl;
    private String status;

    /**
     * Either {@code "INVOICE"} (default) or {@code "PROFORMA"}. Frontend sets
     * PROFORMA to create a proforma sale (no stock deduction, no ledger post,
     * PI/YY-YY/NNNNN number series).
     */
    private String saleType;

    /** Populated on GET when this row is a real invoice created from a proforma. */
    private Long proformaSourceSaleId;
    private String proformaSourceInvoiceNo;

    private java.time.LocalDate dueDate;
    private BigDecimal invoiceDiscount;
    private BigDecimal shippingCharges;
    private BigDecimal otherCharges;

    /** Indian GST Place of Supply (e.g., "27-Maharashtra" or "19-West Bengal") */
    private String placeOfSupply;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CustomerDto getCustomer() { return customer; }
    public void setCustomer(CustomerDto customer) { this.customer = customer; }

    public List<SaleItemDto> getItems() { return items; }
    public void setItems(List<SaleItemDto> items) { this.items = items; }

    public List<PaymentDto> getPaymentDetails() { return paymentDetails; }
    public void setPaymentDetails(List<PaymentDto> paymentDetails) { this.paymentDetails = paymentDetails; }

    public DeliveryDTO getDelivery() { return delivery; }
    public void setDelivery(DeliveryDTO delivery) { this.delivery = delivery; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public BigDecimal getDueAmount() { return dueAmount; }
    public void setDueAmount(BigDecimal dueAmount) { this.dueAmount = dueAmount; }

    public BigDecimal getRoundOff() { return roundOff; }
    public void setRoundOff(BigDecimal roundOff) { this.roundOff = roundOff; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public Boolean getIsGstRequired() { return isGstRequired; }
    public void setIsGstRequired(Boolean isGstRequired) { this.isGstRequired = isGstRequired; }

    public Boolean getReverseCharge() { return reverseCharge; }
    public void setReverseCharge(Boolean reverseCharge) { this.reverseCharge = reverseCharge; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public String getSignedInvoiceUrl() { return signedInvoiceUrl; }
    public void setSignedInvoiceUrl(String signedInvoiceUrl) { this.signedInvoiceUrl = signedInvoiceUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSaleType() { return saleType; }
    public void setSaleType(String saleType) { this.saleType = saleType; }

    public Long getProformaSourceSaleId() { return proformaSourceSaleId; }
    public void setProformaSourceSaleId(Long proformaSourceSaleId) { this.proformaSourceSaleId = proformaSourceSaleId; }

    public String getProformaSourceInvoiceNo() { return proformaSourceInvoiceNo; }
    public void setProformaSourceInvoiceNo(String proformaSourceInvoiceNo) { this.proformaSourceInvoiceNo = proformaSourceInvoiceNo; }

    public java.time.LocalDate getDueDate() { return dueDate; }
    public void setDueDate(java.time.LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getInvoiceDiscount() { return invoiceDiscount; }
    public void setInvoiceDiscount(BigDecimal invoiceDiscount) { this.invoiceDiscount = invoiceDiscount; }

    public BigDecimal getShippingCharges() { return shippingCharges; }
    public void setShippingCharges(BigDecimal shippingCharges) { this.shippingCharges = shippingCharges; }

    public BigDecimal getOtherCharges() { return otherCharges; }
    public void setOtherCharges(BigDecimal otherCharges) { this.otherCharges = otherCharges; }

    public String getPlaceOfSupply() { return placeOfSupply; }
    public void setPlaceOfSupply(String placeOfSupply) { this.placeOfSupply = placeOfSupply; }

    // --- Issue 5: E-Invoice & E-Way Bill fields ---
    private String irn;
    private String ackNo;
    private java.time.LocalDateTime ackDate;
    private String qrCodePath;
    private String einvoiceStatus = "NOT_GENERATED";
    private String ewayBillNo;
    private java.time.LocalDateTime ewayBillDate;
    private java.time.LocalDateTime ewayBillValidUntil;
    private String vehicleNumber;
    private String transporterId;
    private String transporterName;

    public String getIrn() { return irn; }
    public void setIrn(String irn) { this.irn = irn; }

    public String getAckNo() { return ackNo; }
    public void setAckNo(String ackNo) { this.ackNo = ackNo; }

    public java.time.LocalDateTime getAckDate() { return ackDate; }
    public void setAckDate(java.time.LocalDateTime ackDate) { this.ackDate = ackDate; }

    public String getQrCodePath() { return qrCodePath; }
    public void setQrCodePath(String qrCodePath) { this.qrCodePath = qrCodePath; }

    public String getEinvoiceStatus() { return einvoiceStatus; }
    public void setEinvoiceStatus(String einvoiceStatus) { this.einvoiceStatus = einvoiceStatus; }

    public String getEwayBillNo() { return ewayBillNo; }
    public void setEwayBillNo(String ewayBillNo) { this.ewayBillNo = ewayBillNo; }

    public java.time.LocalDateTime getEwayBillDate() { return ewayBillDate; }
    public void setEwayBillDate(java.time.LocalDateTime ewayBillDate) { this.ewayBillDate = ewayBillDate; }

    public java.time.LocalDateTime getEwayBillValidUntil() { return ewayBillValidUntil; }
    public void setEwayBillValidUntil(java.time.LocalDateTime ewayBillValidUntil) { this.ewayBillValidUntil = ewayBillValidUntil; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getTransporterId() { return transporterId; }
    public void setTransporterId(String transporterId) { this.transporterId = transporterId; }

    public String getTransporterName() { return transporterName; }
    public void setTransporterName(String transporterName) { this.transporterName = transporterName; }
}
