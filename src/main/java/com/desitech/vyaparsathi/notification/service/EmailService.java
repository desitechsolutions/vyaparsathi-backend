package com.desitech.vyaparsathi.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // Import this
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.mail.display-name:VyaparSathi}")
    private String displayName;

    public void sendEmail(String to, String subject, String htmlContent) throws MessagingException {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setFrom(fromAddress, displayName);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send email to: {}", to, e);
            throw new MessagingException("Email dispatch failed", e);
        }
    }

    /**
     * Overload with a single in-memory attachment (PDF for the PO / invoice /
     * quotation "send to supplier" flow). Contents are held only for the
     * duration of the send — nothing gets persisted here; callers that need
     * the doc archived should also store it via {@link com.desitech.vyaparsathi.common.util.FileStorageService}.
     */
    public void sendEmailWithAttachment(String to, String subject, String htmlContent,
                                        String attachmentName, byte[] attachmentBytes,
                                        String mimeType) throws MessagingException {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true so we can attach a file alongside the HTML body.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setFrom(fromAddress, displayName);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            if (attachmentBytes != null && attachmentBytes.length > 0 && attachmentName != null) {
                jakarta.activation.DataSource src =
                        new jakarta.mail.util.ByteArrayDataSource(attachmentBytes,
                                mimeType != null ? mimeType : "application/octet-stream");
                helper.addAttachment(attachmentName, src);
            }

            mailSender.send(message);
            logger.info("Email + attachment sent to {}: {}", to, attachmentName);
        } catch (Exception e) {
            logger.error("Failed to send email with attachment to {}: {}", to, attachmentName, e);
            throw new MessagingException("Email dispatch (with attachment) failed", e);
        }
    }
}