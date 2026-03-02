package com.desitech.vyaparsathi.invoice.utils;

import com.desitech.vyaparsathi.invoice.service.InvoiceService;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.lowagie.text.Element;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static java.math.BigDecimal.ZERO;

@Component
public class InvoiceUtil {
    private static final Logger logger = LoggerFactory.getLogger(InvoiceUtil.class);

    @Value("${spring.file.upload.dir:gs://vyaparsathi_s3_bucket/}")
    private String uploadDir;
    @Autowired(required = false)
    private Storage storage;



    public static String formatBankDetails(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        // 1. If it already contains newlines, user likely formatted it manually; return as is.
        if (raw.contains("\n")) return raw.trim();

        String bankName = "";
        String accNo = "";
        String ifsc = "";
        String upi = "";

        // Normalize: remove extra spaces and common labels/colons to clean the search area
        String workingStr = raw.trim().replaceAll("(?i)(Bank Name|Account No|A/C No|IFSC Code|Bank|A/C|IFSC|:)", " ")
                .replaceAll("\\s+", " ");

        // 2. Extract IFSC (Standard: 4 alpha + 0 + 6 alphanumeric)
        java.util.regex.Matcher ifscMatcher =
                java.util.regex.Pattern.compile("(?i)([A-Z]{4}0[A-Z0-9]{6})").matcher(workingStr);
        if (ifscMatcher.find()) {
            ifsc = ifscMatcher.group().toUpperCase();
            workingStr = workingStr.replace(ifscMatcher.group(), " ");
        }

        // 3. Extract UPI ID (Contains @)
        java.util.regex.Matcher upiMatcher =
                java.util.regex.Pattern.compile("([a-zA-Z0-9.\\-_]{2,}@[a-zA-Z]{2,})").matcher(workingStr);
        if (upiMatcher.find()) {
            upi = upiMatcher.group().toLowerCase();
            workingStr = workingStr.replace(upiMatcher.group(), " ");
        }

        // 4. Extract Account Number (9 to 18 digits)
        // We use word boundaries \\b to ensure we don't grab part of a phone number or IFSC
        java.util.regex.Matcher accMatcher =
                java.util.regex.Pattern.compile("\\b\\d{9,18}\\b").matcher(workingStr);
        if (accMatcher.find()) {
            accNo = accMatcher.group();
            workingStr = workingStr.replace(accNo, " ");
        }

        // 5. Remaining text is the Bank Name
        bankName = workingStr.trim().replaceAll("\\s{2,}", " ");

        // Build the formatted string
        StringJoiner sj = new StringJoiner("\n");
        if (!bankName.isEmpty()) sj.add("Bank: " + bankName.toUpperCase());
        if (!accNo.isEmpty())    sj.add("A/C: " + accNo);
        if (!ifsc.isEmpty())     sj.add("IFSC: " + ifsc);
        if (!upi.isEmpty())      sj.add("UPI: " + upi);

        return sj.toString();
    }

    public static String numberToWords(BigDecimal number) {
        if (number == null || number.compareTo(ZERO) == 0) return "Zero";

        String[] units = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        String[] scales = {"", "Thousand", "Lakh", "Crore"};

        long whole = number.longValue();
        StringBuilder sb = new StringBuilder();
        int scaleIdx = 0;

        while (whole > 0) {
            long chunk = whole % 1000;
            if (chunk > 0) {
                String chunkText = convertChunk((int) chunk, units, tens);
                if (scaleIdx > 0) chunkText += " " + scales[scaleIdx];
                sb.insert(0, chunkText + (sb.length() > 0 ? " " : ""));
            }
            whole /= 1000;
            scaleIdx++;
        }

        int paise = number.subtract(new BigDecimal(number.longValue())).movePointRight(2).abs().intValue();
        if (paise > 0) {
            sb.append(" and ").append(convertChunk(paise, units, tens)).append(" Paise");
        }

        return sb.toString().trim() + " Rupees";
    }

    private static String convertChunk(int n, String[] units, String[] tens) {
        StringBuilder sb = new StringBuilder();
        if (n >= 100) {
            sb.append(units[n / 100]).append(" Hundred");
            n %= 100;
            if (n > 0) sb.append(" and ");
        }
        if (n >= 20) {
            sb.append(tens[n / 10]);
            n %= 10;
            if (n > 0) sb.append(" ").append(units[n]);
        } else if (n > 0) {
            sb.append(units[n]);
        }
        return sb.toString();
    }

    public byte[] loadImageBytes(String path, String type) {

        if (path == null || path.isBlank()) {
            return null;
        }

        try {

            // ===============================
            // 1. GCS STORAGE (PRIMARY - PROD)
            // ===============================
            if (storage != null && !path.startsWith("http")) {

                Blob blob = storage.get(extractBucketName(uploadDir), path);

                if (blob == null || !blob.exists()) {
                    logger.warn("{} not found in GCS: {}", type, path);
                    return null;
                }

                // Optional safety limit (5MB)
                if (blob.getSize() > 5 * 1024 * 1024) {
                    logger.warn("{} too large: {}", type, path);
                    return null;
                }

                return blob.getContent();
            }

            // ===============================
            // 2. HTTP URL (LEGACY SUPPORT)
            // ===============================
            if (path.startsWith("http")) {
                try (InputStream is = new URL(path).openStream()) {
                    return is.readAllBytes();
                }
            }

            // ===============================
            // 3. LOCAL FILE (DEV MODE)
            // ===============================
            Path localPath = Paths.get(path);

            if (!Files.exists(localPath)) {
                logger.warn("{} local file missing: {}", type, path);
                return null;
            }

            return Files.readAllBytes(localPath);

        } catch (Exception ex) {
            logger.error("Failed loading {} from {}", type, path, ex);
            return null;
        }
    }

    private String extractBucketName(String gsUri) {
        if (!gsUri.startsWith("gs://")) return gsUri;
        String cleaned = gsUri.substring(5); // remove gs://
        int slash = cleaned.indexOf('/');
        return (slash == -1) ? cleaned : cleaned.substring(0, slash);
    }

    public static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            String color = hex.startsWith("#") ? hex : "#" + hex;
            return Color.decode(color);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static PdfPCell createCell(String text, com.lowagie.text.Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    public static void addTotalRow(PdfPTable table, String label, String value, com.lowagie.text.Font font) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, font));
        lCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, font));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(vCell);
    }


}
