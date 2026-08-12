package com.desitech.vyaparsathi.receipt.service;

import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.receipt.dto.PaymentReceiptDto;
import com.desitech.vyaparsathi.receipt.entity.PaymentReceipt;
import com.desitech.vyaparsathi.receipt.repository.PaymentReceiptRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Creates and renders {@link PaymentReceipt} PDF documents.
 *
 * <p>Receipt is created eagerly at payment time (see {@code PaymentServiceImpl})
 * so the same {@code receiptNumber} can be reprinted later. PDF is rendered
 * on demand via {@link #generatePdfByReceiptId(Long)}.
 */
@Service
public class PaymentReceiptService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentReceiptService.class);

    // Match the invoice design system so receipts feel like the same product family
    private static final Color BRAND_FALLBACK    = new Color(41, 128, 185);
    private static final Color SECTION_LABEL_BG  = new Color(243, 244, 246);
    private static final Color BORDER_LIGHT      = new Color(229, 231, 235);
    private static final Color TEXT_MUTED        = new Color(107, 114, 128);
    private static final Color TEXT_STRONG       = new Color(17, 24, 39);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final java.text.NumberFormat currency =
            java.text.NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Autowired private PaymentReceiptRepository receiptRepository;
    @Autowired private PaymentReceiptNumberService numberService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private InvoiceUtil invoiceUtil;

    /**
     * Creates a persisted receipt for a just-recorded payment.
     * Idempotent: if a receipt already exists for this payment it is returned as-is.
     */
    @Transactional
    public PaymentReceipt createFromPayment(Payment payment) {
        if (payment == null || payment.getId() == null) {
            throw new IllegalArgumentException("Payment must be persisted before creating a receipt");
        }

        // Idempotency — never issue two receipts for the same payment
        return receiptRepository.findByPaymentId(payment.getId()).orElseGet(() -> {
            PaymentReceipt receipt = new PaymentReceipt();
            receipt.setShop(payment.getShop());
            receipt.setPaymentId(payment.getId());
            receipt.setCustomerId(payment.getCustomerId());
            receipt.setReceiptDate(payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDateTime.now());
            receipt.setAmount(payment.getAmount());
            receipt.setPaymentMethod(payment.getPaymentMethod());
            receipt.setReference(payment.getReference());
            receipt.setNotes(payment.getNotes());

            Long shopId = payment.getShop() != null ? payment.getShop().getId() : null;
            receipt.setReceiptNumber(
                    numberService.nextReceiptNumber(shopId, receipt.getReceiptDate().toLocalDate()));

            PaymentReceipt saved = receiptRepository.save(receipt);
            logger.info("Issued PaymentReceipt {} for paymentId={} amount={}",
                    saved.getReceiptNumber(), payment.getId(), payment.getAmount());
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public byte[] generatePdfByReceiptId(Long receiptId) {
        PaymentReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new EntityNotFoundAppException("Payment Receipt", receiptId));
        return renderPdf(receipt);
    }

    @Transactional(readOnly = true)
    public byte[] generatePdfByPaymentId(Long paymentId) {
        PaymentReceipt receipt = receiptRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new EntityNotFoundAppException("Payment Receipt (paymentId)", paymentId));
        return renderPdf(receipt);
    }

    @Transactional(readOnly = true)
    public PaymentReceiptDto toDto(PaymentReceipt r) {
        PaymentReceiptDto dto = new PaymentReceiptDto();
        dto.setId(r.getId());
        dto.setReceiptNumber(r.getReceiptNumber());
        dto.setPaymentId(r.getPaymentId());
        dto.setCustomerId(r.getCustomerId());
        dto.setReceiptDate(r.getReceiptDate());
        dto.setAmount(r.getAmount());
        dto.setPaymentMethod(r.getPaymentMethod());
        dto.setReference(r.getReference());
        dto.setNotes(r.getNotes());
        if (r.getCustomerId() != null) {
            customerRepository.findById(r.getCustomerId())
                    .ifPresent(c -> dto.setCustomerName(c.getName()));
        }
        return dto;
    }

    // ─── PDF rendering ──────────────────────────────────────────────────────

    private byte[] renderPdf(PaymentReceipt receipt) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, baos);
            document.open();

            Shop shop = receipt.getShop();
            Color brand = InvoiceUtil.parseColor(shop != null ? shop.getBrandColor() : null, BRAND_FALLBACK);

            Font titleFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.NORMAL, brand);
            Font sectionLabel  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, TEXT_MUTED);
            Font metaLabel     = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
            Font metaValue     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
            Font body          = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, TEXT_STRONG);
            Font bodyBold      = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL, TEXT_STRONG);
            Font amountFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.NORMAL, brand);
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
                Paragraph addr = new Paragraph(shop.getAddress(), small);
                addr.setLeading(11f);
                left.addElement(addr);
            }
            if (shop != null && shop.getGstin() != null) {
                left.addElement(new Paragraph("GSTIN: " + shop.getGstin(), small));
            }
            header.addCell(left);

            PdfPCell right = new PdfPCell();
            right.setBorder(Rectangle.NO_BORDER);
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph title = new Paragraph("PAYMENT RECEIPT", titleFont);
            title.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(title);

            PdfPTable meta = new PdfPTable(2);
            meta.setTotalWidth(180);
            meta.setLockedWidth(true);
            meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
            meta.setSpacingBefore(6f);
            addMetaRow(meta, "Receipt No", receipt.getReceiptNumber(), metaLabel, metaValue);
            addMetaRow(meta, "Date", DATE_FMT.format(receipt.getReceiptDate()), metaLabel, metaValue);
            right.addElement(meta);
            header.addCell(right);
            document.add(header);

            // Brand rule
            PdfPTable rule = new PdfPTable(1);
            rule.setWidthPercentage(100);
            rule.setSpacingBefore(10f);
            PdfPCell rc = new PdfPCell();
            rc.setFixedHeight(2f);
            rc.setBorder(Rectangle.BOTTOM);
            rc.setBorderColorBottom(brand);
            rc.setBorderWidthBottom(1.5f);
            rule.addCell(rc);
            document.add(rule);

            // Amount card
            PdfPTable card = new PdfPTable(1);
            card.setWidthPercentage(100);
            card.setSpacingBefore(14f);
            PdfPCell cardCell = new PdfPCell();
            cardCell.setBackgroundColor(SECTION_LABEL_BG);
            cardCell.setBorder(Rectangle.BOX);
            cardCell.setBorderColor(BORDER_LIGHT);
            cardCell.setBorderWidth(1f);
            cardCell.setBorderColorLeft(brand);
            cardCell.setBorderWidthLeft(3f);
            cardCell.setPadding(14);

            Paragraph amtLabel = new Paragraph("AMOUNT RECEIVED", sectionLabel);
            cardCell.addElement(amtLabel);
            Paragraph amount = new Paragraph(currency.format(receipt.getAmount()), amountFont);
            amount.setSpacingBefore(4f);
            cardCell.addElement(amount);
            Paragraph words = new Paragraph(
                    "In words: " + InvoiceUtil.numberToWords(receipt.getAmount()) + " Only", small);
            words.setSpacingBefore(2f);
            cardCell.addElement(words);
            card.addCell(cardCell);
            document.add(card);

            // Received From / Payment Details
            PdfPTable details = new PdfPTable(2);
            details.setWidthPercentage(100);
            details.setSpacingBefore(14f);
            details.setWidths(new float[]{50, 50});

            details.addCell(sectionLabelStrip("RECEIVED FROM", sectionLabel));
            details.addCell(sectionLabelStrip("PAYMENT DETAILS", sectionLabel));

            PdfPCell fromCell = new PdfPCell();
            fromCell.setBorder(Rectangle.BOX);
            fromCell.setBorderColor(BORDER_LIGHT);
            fromCell.setBorderWidth(0.5f);
            fromCell.setPadding(10);
            Customer customer = null;
            if (receipt.getCustomerId() != null) {
                customer = customerRepository.findById(receipt.getCustomerId()).orElse(null);
            }
            if (customer != null) {
                Paragraph cn = new Paragraph(customer.getName(), bodyBold);
                cn.setLeading(13f);
                fromCell.addElement(cn);
                if (customer.getPhone() != null) {
                    fromCell.addElement(new Paragraph(customer.getPhone(), body));
                }
                if (customer.getAddressLine1() != null) {
                    Paragraph a = new Paragraph(customer.getAddressLine1(), small);
                    a.setLeading(11f);
                    fromCell.addElement(a);
                }
            } else {
                fromCell.addElement(new Paragraph("Walk-in Customer", body));
            }
            details.addCell(fromCell);

            PdfPCell payCell = new PdfPCell();
            payCell.setBorder(Rectangle.BOX);
            payCell.setBorderColor(BORDER_LIGHT);
            payCell.setBorderWidth(0.5f);
            payCell.setPadding(10);
            payCell.addElement(kvRow("Method", receipt.getPaymentMethod() != null ? receipt.getPaymentMethod().name() : "-", metaLabel, metaValue));
            payCell.addElement(kvRow("Reference", isBlank(receipt.getReference()) ? "—" : receipt.getReference(), metaLabel, metaValue));

            Payment pay = paymentRepository.findById(receipt.getPaymentId()).orElse(null);
            if (pay != null && pay.getSourceType() != null && pay.getSourceId() != null) {
                String against = pay.getSourceType().name() + " #" + pay.getSourceId();
                if ("SALE".equals(pay.getSourceType().name())) {
                    Sale sale = saleRepository.findById(pay.getSourceId()).orElse(null);
                    if (sale != null && sale.getInvoiceNo() != null) {
                        against = "Invoice " + sale.getInvoiceNo();
                    }
                }
                payCell.addElement(kvRow("Against", against, metaLabel, metaValue));
            } else if (pay != null) {
                payCell.addElement(kvRow("Against", "Customer Advance", metaLabel, metaValue));
            }
            details.addCell(payCell);
            document.add(details);

            // Notes
            if (!isBlank(receipt.getNotes())) {
                PdfPTable notesWrap = new PdfPTable(1);
                notesWrap.setWidthPercentage(100);
                notesWrap.setSpacingBefore(12f);
                notesWrap.addCell(sectionLabelStrip("NOTES", sectionLabel));
                PdfPCell notesCell = new PdfPCell(new Phrase(receipt.getNotes(), body));
                notesCell.setBorder(Rectangle.BOX);
                notesCell.setBorderColor(BORDER_LIGHT);
                notesCell.setBorderWidth(0.5f);
                notesCell.setPadding(10);
                notesWrap.addCell(notesCell);
                document.add(notesWrap);
            }

            // Footer
            Paragraph footer = new Paragraph(
                    "Thank you for your payment. This is a system-generated receipt.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Font.NORMAL, TEXT_MUTED));
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(24f);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render receipt PDF for id={}", receipt.getId(), e);
            throw new ExportAppException("Failed to render payment receipt PDF for id: " + receipt.getId(), e);
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
