package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.CustomerNotFoundException;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.notification.service.EmailService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CustomerEmailService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerEmailService.class);

    private final CustomerRepository customerRepo;
    private final ShopRepository shopRepo;
    private final CustomerStatementPdfService statementPdfService;
    private final EmailService emailService;
    private final CustomerAuditService auditService;

    public CustomerEmailService(CustomerRepository customerRepo,
                                ShopRepository shopRepo,
                                CustomerStatementPdfService statementPdfService,
                                EmailService emailService,
                                CustomerAuditService auditService) {
        this.customerRepo = customerRepo;
        this.shopRepo = shopRepo;
        this.statementPdfService = statementPdfService;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public void sendStatementEmail(Long customerId, LocalDateTime startDate, LocalDateTime endDate, String customMessage) {
        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new IllegalArgumentException("Customer does not have a registered email address.");
        }

        Long shopId = TenantContext.getCurrentShopId();
        Shop shop = shopId != null ? shopRepo.findById(shopId).orElse(customer.getShop()) : customer.getShop();
        String shopName = shop != null && shop.getName() != null ? shop.getName() : "VyaparSathi";

        byte[] pdfBytes = statementPdfService.generateStatementPdf(customerId, startDate, endDate);

        String subject = "Account Statement from " + shopName;
        String htmlBody = "<div style='font-family: Arial, sans-serif; color: #333; line-height: 1.6;'>" +
                "<h2>Hello " + (customer.getName() != null ? customer.getName() : "Customer") + ",</h2>" +
                "<p>Please find attached your latest statement of account from <strong>" + shopName + "</strong>.</p>" +
                (customMessage != null && !customMessage.isBlank() ? "<div style='background: #f3f4f6; padding: 12px; border-left: 4px solid #2563eb; margin: 15px 0;'>" + customMessage + "</div>" : "") +
                "<p>If you have any questions regarding this statement or your outstanding balance, please feel free to reach out to us.</p>" +
                "<br/><p>Warm regards,<br/><strong>" + shopName + "</strong></p>" +
                "</div>";

        try {
            emailService.sendEmailWithAttachment(
                    customer.getEmail().trim(),
                    subject,
                    htmlBody,
                    "Statement_" + customer.getName().replaceAll("[^a-zA-Z0-9]", "_") + ".pdf",
                    pdfBytes,
                    "application/pdf"
            );
            logger.info("Sent statement email to customerId={}, email={}", customerId, customer.getEmail());
            auditService.recordAction(customerId, "STATEMENT_EMAILED", "Statement sent via email to " + customer.getEmail(), null);
        } catch (Exception e) {
            logger.error("Failed to send statement email to customerId={}: {}", customerId, e.getMessage(), e);
            throw new RuntimeException("Failed to send statement email: " + e.getMessage(), e);
        }
    }
}
