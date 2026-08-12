package com.desitech.vyaparsathi.refund.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.customer.dto.CustomerLedgerDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.entity.CustomerLedgerType;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.customer.service.CustomerLedgerService;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.refund.dto.RefundDto;
import com.desitech.vyaparsathi.refund.dto.RefundRequest;
import com.desitech.vyaparsathi.refund.entity.Refund;
import com.desitech.vyaparsathi.refund.enums.RefundStatus;
import com.desitech.vyaparsathi.refund.repository.RefundRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Processes customer refunds and renders their receipt PDF.
 *
 * <p>{@link #processRefund(Payment, RefundRequest)} runs inside the calling
 * transaction: it validates that the requested amount does not exceed the
 * refundable balance (original payment amount minus prior refunds), issues a
 * numbered Refund, and posts a matching {@link CustomerLedgerType#CREDIT}
 * ledger entry that returns the debt to the customer.
 */
@Service
public class RefundService {

    private static final Logger logger = LoggerFactory.getLogger(RefundService.class);

    // Same palette as invoice / receipt PDFs so all documents feel like the same product family
    private static final Color BRAND_FALLBACK    = new Color(41, 128, 185);
    private static final Color SECTION_LABEL_BG  = new Color(243, 244, 246);
    private static final Color BORDER_LIGHT      = new Color(229, 231, 235);
    private static final Color TEXT_MUTED        = new Color(107, 114, 128);
    private static final Color TEXT_STRONG       = new Color(17, 24, 39);
    private static final Color REFUND_ACCENT     = new Color(231, 76, 60);   // red — matches "balance due" tone in invoice

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final java.text.NumberFormat currency =
            java.text.NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Autowired private RefundRepository refundRepository;
    @Autowired private RefundNumberService numberService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerLedgerService ledgerService;

    /**
     * Records a refund against {@code originalPayment} and posts the reversal
     * ledger entry. Must be called inside the caller's transaction so that
     * ledger + refund row are atomically persisted.
     */
    @Transactional
    public Refund processRefund(Payment originalPayment, RefundRequest request) {
        if (originalPayment == null || originalPayment.getId() == null) {
            throw new IllegalArgumentException("Original payment must be persisted");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new BusinessValidationException("Refund amount must be positive");
        }

        BigDecimal alreadyRefunded = refundRepository.sumRefundedByPaymentId(originalPayment.getId());
        BigDecimal refundable = originalPayment.getAmount().subtract(alreadyRefunded).max(BigDecimal.ZERO);
        if (request.getAmount().compareTo(refundable) > 0) {
            throw new BusinessValidationException(
                    "Refund amount " + request.getAmount() + " exceeds refundable balance of " + refundable);
        }

        Refund refund = new Refund();
        refund.setShop(originalPayment.getShop());
        refund.setOriginalPaymentId(originalPayment.getId());
        refund.setCustomerId(originalPayment.getCustomerId());
        refund.setRefundDate(LocalDateTime.now());
        refund.setAmount(request.getAmount());
        refund.setPaymentMethod(request.getPaymentMethod());
        refund.setReference(request.getReference());
        refund.setNotes(request.getNotes());
        refund.setStatus(RefundStatus.COMPLETED);

        Long shopId = originalPayment.getShop() != null ? originalPayment.getShop().getId() : null;
        refund.setRefundNo(numberService.nextRefundNumber(shopId, refund.getRefundDate().toLocalDate()));

        Refund saved = refundRepository.save(refund);

        // Reverse the money-owed direction on the customer ledger:
        // CREDIT increases the amount the shop owes the customer (or reduces
        // their advance balance). This mirrors how the original payment was
        // recorded as a DEBIT.
        if (originalPayment.getCustomerId() != null) {
            CustomerLedgerDto ledgerDto = new CustomerLedgerDto();
            ledgerDto.setAmount(request.getAmount());
            ledgerDto.setType(CustomerLedgerType.CREDIT);
            ledgerDto.setDescription("Refund " + saved.getRefundNo() +
                    " against Payment #" + originalPayment.getId() +
                    (saved.getReference() != null ? " (Ref: " + saved.getReference() + ")" : ""));
            ledgerService.addEntry(originalPayment.getCustomerId(), ledgerDto);
        }

        logger.info("Issued Refund {} for paymentId={} amount={}",
                saved.getRefundNo(), originalPayment.getId(), saved.getAmount());
        return saved;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdfByRefundId(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new EntityNotFoundAppException("Refund", refundId));
        return renderPdf(refund);
    }

    @Transactional(readOnly = true)
    public RefundDto toDto(Refund r) {
        RefundDto dto = new RefundDto();
        dto.setId(r.getId());
        dto.setRefundNo(r.getRefundNo());
        dto.setOriginalPaymentId(r.getOriginalPaymentId());
        dto.setCustomerId(r.getCustomerId());
        dto.setRefundDate(r.getRefundDate());
        dto.setAmount(r.getAmount());
        dto.setPaymentMethod(r.getPaymentMethod());
        dto.setReference(r.getReference());
        dto.setNotes(r.getNotes());
        dto.setStatus(r.getStatus());
        if (r.getCustomerId() != null) {
            customerRepository.findById(r.getCustomerId()).ifPresent(c -> dto.setCustomerName(c.getName()));
        }
        return dto;
    }

    // ─── PDF rendering ──────────────────────────────────────────────────

    private byte[] renderPdf(Refund refund) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Shop shop = refund.getShop();
            Color brand = InvoiceUtil.parseColor(shop != null ? shop.getBrandColor() : null, BRAND_FALLBACK);

            Font titleFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.NORMAL, REFUND_ACCENT);
            Font sectionLabel  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, TEXT_MUTED);
            Font metaLabel     = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
            Font metaValue     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
            Font body          = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, TEXT_STRONG);
            Font bodyBold      = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL, TEXT_STRONG);
            Font amountFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.NORMAL, REFUND_ACCENT);
            Font small         = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);

            // Header
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{60, 40});

            PdfPCell left = new PdfPCell();
            left.setBorder(Rectangle.NO_BORDER);
            Paragraph shopName = new Paragraph(
                    shop != null && shop.getName() != null ? shop.getName().toUpperCase() : "SHOP",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.NORMAL, TEXT_STRONG));
            shopName.setLeading(16f);
            left.addElement(shopName);
            if (shop != null && shop.getAddress() != null) {
                left.addElement(new Paragraph(shop.getAddress(), small));
            }
            if (shop != null && shop.getGstin() != null) {
                left.addElement(new Paragraph("GSTIN: " + shop.getGstin(), small));
            }
            header.addCell(left);

            PdfPCell right = new PdfPCell();
            right.setBorder(Rectangle.NO_BORDER);
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph title = new Paragraph("REFUND RECEIPT", titleFont);
            title.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(title);

            PdfPTable meta = new PdfPTable(2);
            meta.setTotalWidth(180);
            meta.setLockedWidth(true);
            meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
            meta.setSpacingBefore(6f);
            addMetaRow(meta, "Refund No", refund.getRefundNo(), metaLabel, metaValue);
            addMetaRow(meta, "Date", DATE_FMT.format(refund.getRefundDate()), metaLabel, metaValue);
            addMetaRow(meta, "Status", refund.getStatus() != null ? refund.getStatus().name() : "-", metaLabel, metaValue);
            right.addElement(meta);
            header.addCell(right);
            doc.add(header);

            // Brand rule (red for refund emphasis)
            PdfPTable rule = new PdfPTable(1);
            rule.setWidthPercentage(100);
            rule.setSpacingBefore(10f);
            PdfPCell rc = new PdfPCell();
            rc.setFixedHeight(2f);
            rc.setBorder(Rectangle.BOTTOM);
            rc.setBorderColorBottom(REFUND_ACCENT);
            rc.setBorderWidthBottom(1.5f);
            rule.addCell(rc);
            doc.add(rule);

            // Amount card
            PdfPTable card = new PdfPTable(1);
            card.setWidthPercentage(100);
            card.setSpacingBefore(14f);
            PdfPCell cardCell = new PdfPCell();
            cardCell.setBackgroundColor(SECTION_LABEL_BG);
            cardCell.setBorder(Rectangle.BOX);
            cardCell.setBorderColor(BORDER_LIGHT);
            cardCell.setBorderWidth(1f);
            cardCell.setBorderColorLeft(REFUND_ACCENT);
            cardCell.setBorderWidthLeft(3f);
            cardCell.setPadding(14);

            cardCell.addElement(new Paragraph("REFUND AMOUNT", sectionLabel));
            Paragraph amount = new Paragraph(currency.format(refund.getAmount()), amountFont);
            amount.setSpacingBefore(4f);
            cardCell.addElement(amount);
            Paragraph words = new Paragraph(
                    "In words: " + InvoiceUtil.numberToWords(refund.getAmount()) + " Only", small);
            words.setSpacingBefore(2f);
            cardCell.addElement(words);
            card.addCell(cardCell);
            doc.add(card);

            // Refund To / Refund Details
            PdfPTable details = new PdfPTable(2);
            details.setWidthPercentage(100);
            details.setSpacingBefore(14f);
            details.setWidths(new float[]{50, 50});

            details.addCell(sectionLabelStrip("REFUNDED TO", sectionLabel));
            details.addCell(sectionLabelStrip("REFUND DETAILS", sectionLabel));

            PdfPCell toCell = new PdfPCell();
            toCell.setBorder(Rectangle.BOX);
            toCell.setBorderColor(BORDER_LIGHT);
            toCell.setBorderWidth(0.5f);
            toCell.setPadding(10);
            Customer customer = refund.getCustomerId() != null
                    ? customerRepository.findById(refund.getCustomerId()).orElse(null) : null;
            if (customer != null) {
                Paragraph cn = new Paragraph(customer.getName(), bodyBold);
                cn.setLeading(13f);
                toCell.addElement(cn);
                if (customer.getPhone() != null) toCell.addElement(new Paragraph(customer.getPhone(), body));
                if (customer.getAddressLine1() != null) {
                    Paragraph a = new Paragraph(customer.getAddressLine1(), small);
                    a.setLeading(11f);
                    toCell.addElement(a);
                }
            } else {
                toCell.addElement(new Paragraph("Walk-in Customer", body));
            }
            details.addCell(toCell);

            PdfPCell refundCell = new PdfPCell();
            refundCell.setBorder(Rectangle.BOX);
            refundCell.setBorderColor(BORDER_LIGHT);
            refundCell.setBorderWidth(0.5f);
            refundCell.setPadding(10);
            refundCell.addElement(kvRow("Method",
                    refund.getPaymentMethod() != null ? refund.getPaymentMethod().name() : "-", metaLabel, metaValue));
            refundCell.addElement(kvRow("Reference",
                    isBlank(refund.getReference()) ? "—" : refund.getReference(), metaLabel, metaValue));
            refundCell.addElement(kvRow("Against Payment", "#" + refund.getOriginalPaymentId(), metaLabel, metaValue));
            details.addCell(refundCell);
            doc.add(details);

            // Notes
            if (!isBlank(refund.getNotes())) {
                PdfPTable notesWrap = new PdfPTable(1);
                notesWrap.setWidthPercentage(100);
                notesWrap.setSpacingBefore(12f);
                notesWrap.addCell(sectionLabelStrip("NOTES", sectionLabel));
                PdfPCell notesCell = new PdfPCell(new Phrase(refund.getNotes(), body));
                notesCell.setBorder(Rectangle.BOX);
                notesCell.setBorderColor(BORDER_LIGHT);
                notesCell.setBorderWidth(0.5f);
                notesCell.setPadding(10);
                notesWrap.addCell(notesCell);
                doc.add(notesWrap);
            }

            Paragraph footer = new Paragraph(
                    "This refund receipt confirms that the amount above has been returned to the customer.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Font.NORMAL, TEXT_MUTED));
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(24f);
            doc.add(footer);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render refund PDF for id={}", refund.getId(), e);
            throw new ExportAppException("Failed to render refund PDF: " + refund.getId(), e);
        }
    }

    private void addMetaRow(PdfPTable meta, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        l.setPaddingRight(6);
        l.setPaddingTop(2);
        l.setPaddingBottom(2);
        meta.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(2);
        v.setPaddingBottom(2);
        meta.addCell(v);
    }

    private Paragraph kvRow(String label, String value, Font labelFont, Font valueFont) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + ": ", labelFont));
        p.add(new Chunk(value != null ? value : "-", valueFont));
        p.setLeading(13f);
        return p;
    }

    private PdfPCell sectionLabelStrip(String label, Font labelFont) {
        PdfPCell cell = new PdfPCell(new Phrase(label, labelFont));
        cell.setBackgroundColor(SECTION_LABEL_BG);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
