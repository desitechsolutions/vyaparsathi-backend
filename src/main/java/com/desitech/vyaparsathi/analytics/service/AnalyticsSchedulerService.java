package com.desitech.vyaparsathi.analytics.service;

import com.desitech.vyaparsathi.analytics.dto.ChurnPredictionDto;
import com.desitech.vyaparsathi.analytics.dto.PurchaseOrderSuggestionDto;
import com.desitech.vyaparsathi.analytics.dto.ItemDemandPredictionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.List;

@Service
public class AnalyticsSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsSchedulerService.class);

    @Autowired private AnalyticsService analyticsService;
    @Autowired private AnalyticsExportService analyticsExportService;
    @Autowired private JavaMailSender mailSender;

    // Configuration - Could be moved to application.properties
    private final String ADMIN_EMAIL = "krs.birendra@gmail.com";
    private final BigDecimal CRITICAL_BUDGET_THRESHOLD = new BigDecimal("50000.00");

    /**
     * CRITICAL ALERT: Low Stock & High Investment
     * Checks every morning at 6 AM if procurement needs exceed budget.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void checkLowStockAndBudgetAlert() {
        logger.info("Running morning procurement budget check...");
        List<PurchaseOrderSuggestionDto> suggestions = analyticsService.suggestFuturePurchaseOrders();

        BigDecimal totalInvestmentNeeded = suggestions.stream()
                .map(PurchaseOrderSuggestionDto::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalInvestmentNeeded.compareTo(CRITICAL_BUDGET_THRESHOLD) > 0) {
            String subject = "⚠️ URGENT: High Procurement Investment Required";
            String body = String.format(
                    "System has detected critical low stock levels. \n\n" +
                            "Estimated Investment Needed: Rs. %s \n" +
                            "Budget Threshold: Rs. %s \n\n" +
                            "Please review the attached procurement plan to prioritize items.",
                    totalInvestmentNeeded.toPlainString(),
                    CRITICAL_BUDGET_THRESHOLD.toPlainString()
            );

            byte[] pdf = analyticsExportService.exportPurchaseSuggestions(suggestions, "pdf");
            sendEmailWithAttachment(ADMIN_EMAIL, subject, body, pdf, "urgent-procurement-plan.pdf");
            logger.warn("Urgent budget alert sent. Investment needed: {}", totalInvestmentNeeded);
        }
    }

    /**
     * Daily Procurement Summary (CSV)
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void sendDailyProcurementReport() {
        List<PurchaseOrderSuggestionDto> data = analyticsService.suggestFuturePurchaseOrders();
        if (!data.isEmpty()) {
            byte[] csv = analyticsExportService.exportPurchaseSuggestions(data, "csv");
            sendEmailWithAttachment(ADMIN_EMAIL, "Daily Procurement Report",
                    "Attached is the daily breakdown of items requiring restock.", csv, "daily-restock.csv");
        }
    }

    /**
     * Weekly Demand & Strategy Report (Excel)
     */
    @Scheduled(cron = "0 0 8 * * MON")
    public void sendWeeklyStrategyReport() {
        // Combining demand prediction with procurement cost
        List<PurchaseOrderSuggestionDto> data = analyticsService.suggestFuturePurchaseOrders();
        byte[] excel = analyticsExportService.exportPurchaseSuggestions(data, "excel");

        sendEmailWithAttachment(ADMIN_EMAIL, "Weekly Inventory Strategy",
                "Detailed weekly procurement and investment forecast attached.", excel, "weekly-strategy.xlsx");
    }

    /**
     * Monthly Business Health Report (PDF)
     */
    @Scheduled(cron = "0 0 9 1 * *")
    public void sendMonthlyHealthReport() {
        List<PurchaseOrderSuggestionDto> data = analyticsService.suggestFuturePurchaseOrders();
        byte[] pdf = analyticsExportService.exportPurchaseSuggestions(data, "pdf");

        sendEmailWithAttachment(ADMIN_EMAIL, "Monthly Business Intelligence Report",
                "Full monthly summary of inventory health and investment needs.", pdf, "monthly-health-report.pdf");
    }

    /**
     * VIP CHURN ALERT
     * Runs every Monday at 10 AM.
     * Identifies high-value customers at risk of leaving.
     */
    @Scheduled(cron = "0 0 10 * * MON")
    public void sendVIPChurnAlerts() {
        logger.info("Analyzing customer churn risks for VIPs...");

        List<ChurnPredictionDto> churnRisks = analyticsService.predictChurn(AnalyticsService.DEFAULT_CHURN_THRESHOLD_DAYS);

        // Define a "High Value" risk as someone with more than ₹10,000 at risk
        List<ChurnPredictionDto> vipRisks = churnRisks.stream()
                .filter(c -> c.getChurnProbability() > 0.8)
                .filter(c -> c.getRevenueAtRisk().compareTo(new BigDecimal("10000")) > 0)
                .toList();

        if (!vipRisks.isEmpty()) {
            StringBuilder body = new StringBuilder();
            body.append("The following VIP customers are at high risk of churning:\n\n");

            for (ChurnPredictionDto risk : vipRisks) {
                body.append(String.format("- %s (Potential Monthly Loss: Rs. %s)\n",
                        risk.getCustomerName(),
                        risk.getRevenueAtRisk().toPlainString()));
            }

            body.append("\nSuggested Action: Send a personalized discount or check-in email.");

            sendEmailWithAttachment(ADMIN_EMAIL, "🚨 VIP Churn Warning", body.toString(), null, null);
            logger.warn("VIP Churn Alert sent for {} customers", vipRisks.size());
        }
    }
    private void sendEmailWithAttachment(String to, String subject, String text, byte[] attachment, String filename) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(attachment));
            mailSender.send(message);
        } catch (Exception e) {
            logger.error("Failed to send scheduled email report: {}", subject, e);
        }
    }

}