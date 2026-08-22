package com.desitech.vyaparsathi.customer.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;

@Entity
@Table(
    name = "customer",
    // Phone uniqueness is scoped to the shop (multi-tenant safe): two
    // different shops may legitimately have a customer with the same
    // phone number. The composite index is enforced by V114 in the
    // database; declaring it here keeps the entity metadata honest so
    // developers reading Customer.java see the actual DB shape.
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_shop_phone", columnNames = {"shop_id", "phone"})
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Customer extends ShopAwareEntity {
    @Column(nullable = false)
    private String name;

    @Column(length = 15)
    private String phone;

    private String email;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    private String postalCode;
    private String country;
    private String gstNumber;
    private String panNumber;
    private String notes;

    @Column(name = "credit_balance", nullable = false)
    private BigDecimal creditBalance = BigDecimal.ZERO;

    /** V99 — legal name (defaults to trade `name` when unset). */
    @Column(name = "legal_name")
    private String legalName;

    // ─── V104 CRM + lifecycle ─────────────────────────────────────────
    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private com.desitech.vyaparsathi.customer.enums.CustomerType customerType =
            com.desitech.vyaparsathi.customer.enums.CustomerType.INDIVIDUAL;

    /** Default net-N receivable terms in days. */
    @Column(name = "credit_days")
    private Integer creditDays;

    /** Max outstanding receivable we'll extend before flagging. */
    @Column(name = "credit_limit", precision = 14, scale = 2)
    private BigDecimal creditLimit;

    /** Free-text override — e.g. "COD only", "NET15", "30 days from GRN". */
    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    /** Comma-separated tags for CRM segmentation. */
    @Column(name = "tags", length = 500)
    private String tags;

    /** Display name distinct from legal — e.g. "Acme" vs "Acme Traders Pvt Ltd". */
    @Column(name = "trade_name")
    private String tradeName;

    /** Individual customer's birthday — birthday-marketing hook. */
    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    /** Anniversary date (wedding / business incorporation). */
    @Column(name = "anniversary_date")
    private java.time.LocalDate anniversaryDate;

    /** Free-text industry for B2B customers. */
    @Column(name = "industry", length = 100)
    private String industry;

    /** Where the customer came from — WALK_IN / REFERRAL / ONLINE / MARKETING / OTHER. */
    @Column(name = "source", length = 30)
    private String source;

    // ─── V115 enterprise columns ─────────────────────────────────────
    /**
     * When TRUE, invoice creation is blocked for this customer via
     * the credit-limit gate — used to pause a customer without
     * archiving them (e.g. cheque bounced, dispute open).
     */
    @Column(name = "credit_hold", nullable = false)
    private Boolean creditHold = Boolean.FALSE;

    /** Sales rep / account manager owning this relationship. FK → users.id. */
    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    /** ISO-4217 currency code — default INR. Multi-currency prerequisite. */
    @Column(name = "preferred_currency", length = 3, nullable = false)
    private String preferredCurrency = "INR";

    /** MSME / Udyam registration number (India). */
    @Column(name = "msme_udyam", length = 30)
    private String msmeUdyam;

    /** TAN — Tax Deduction and Collection Account Number. */
    @Column(name = "tan", length = 20)
    private String tan;

    /**
     * Whether TDS is applicable on payments from / to this customer
     * (deduction under Section 194Q for high-turnover buyers,
     * Section 51 for government customers).
     */
    @Column(name = "tds_applicable", nullable = false)
    private Boolean tdsApplicable = Boolean.FALSE;

    /** DPDPA-compliant opt-in flags for outbound communication. */
    @Column(name = "email_opt_in", nullable = false)
    private Boolean emailOptIn = Boolean.TRUE;

    @Column(name = "sms_opt_in", nullable = false)
    private Boolean smsOptIn = Boolean.TRUE;

    @Column(name = "whatsapp_opt_in", nullable = false)
    private Boolean whatsappOptIn = Boolean.TRUE;

    // ─── Custom-logic accessors ──────────────────────────────────────
    // Fallback getters for display and alias getter for GST interoperability.
    // Standard accessors are generated by Lombok (@Getter @Setter).

    /** Fallback getter: display "trading as" == `name` when trade name isn't set. */
    public String getTradeName() { return tradeName != null ? tradeName : name; }

    /** Fallback getter: legal name shows `name` when legal name isn't set. */
    public String getLegalName() { return legalName != null ? legalName : name; }

    /**
     * Alias getter kept intentionally: lets GstTaxService / GSTR-1 export / e-invoice
     * flows call {@code getGstin()} uniformly on any tax party.
     */
    public String getGstin() { return gstNumber; }
}