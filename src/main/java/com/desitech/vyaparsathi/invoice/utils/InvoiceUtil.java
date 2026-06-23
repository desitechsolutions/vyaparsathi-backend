package com.desitech.vyaparsathi.invoice.utils;

import com.google.cloud.storage.Blob;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import java.awt.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringJoiner;

import static java.math.BigDecimal.ZERO;

@Component
public class InvoiceUtil {
    private static final Logger logger = LoggerFactory.getLogger(InvoiceUtil.class);

    @Value("${spring.file.upload.dir:gs://vyaparsathi_s3_bucket/}")
    private String uploadDir;

    @Autowired(required = false)
    private Storage storage;

    public byte[] loadImageBytes(String dbPath, String type) {
        if (dbPath == null || dbPath.isBlank()) {
            return null;
        }

        try {
            // ===========================================
            // 1. GCS STORAGE (PRIMARY - PROD)
            // ===========================================
            if (storage != null && !dbPath.startsWith("http") && uploadDir.startsWith("gs://")) {
                String bucketName = extractBucketName(uploadDir);
                String objectPath = dbPath.startsWith("gs://") ? extractObjectPath(dbPath) : dbPath;

                logger.info("Attempting GCS Load | Type: {} | Bucket: {} | Object: {}", type, bucketName, objectPath);
                Blob blob = storage.get(bucketName, objectPath);

                if (blob != null && blob.exists()) {
                    return blob.getContent();
                }
            }

            // ===========================================
            // 2. HTTP URL (FALLBACK - Avoid calling localhost)
            // ===========================================
            if (dbPath.startsWith("http")) {
                // Logic to prevent the server from calling itself recursively
                if (dbPath.contains("localhost") || dbPath.contains("127.0.0.1")) {
                    logger.warn("Skipping local HTTP call for {} to avoid deadlock. Using ClassPath instead.", type);
                } else {
                    try (InputStream is = new URL(dbPath).openStream()) {
                        return is.readAllBytes();
                    }
                }
            }

            // ===========================================
            // 3. CLASSPATH LOOKUP (LOCAL DEV)
            // ===========================================
            // This combines "static/uploads/" + "logos/filename.png"
            String fullResourcePath = uploadDir + (dbPath.contains("uploads/") ? dbPath.split("uploads/")[1] : dbPath);
            org.springframework.core.io.Resource resource = new ClassPathResource(fullResourcePath);

            if (resource.exists()) {
                logger.info("Loading {} from ClassPath: {}", type, fullResourcePath);
                try (InputStream is = resource.getInputStream()) {
                    return is.readAllBytes();
                }
            } else {
                logger.warn("File not found in ClassPath: {}", fullResourcePath);
            }

            // ===========================================
            // 4. LOCAL FILE SYSTEM LOOKUP (LOCAL DEV)
            // ===========================================
            java.io.File localFile = new java.io.File("uploads/" + dbPath);
            if (!localFile.exists() && !dbPath.startsWith("logos/") && !dbPath.startsWith("signatures/")) {
                localFile = new java.io.File("uploads/logos/" + dbPath);
                if (!localFile.exists()) {
                    localFile = new java.io.File("uploads/signatures/" + dbPath);
                }
            }
            if (localFile.exists() && localFile.isFile()) {
                logger.info("Loading {} from local filesystem: {}", type, localFile.getAbsolutePath());
                return java.nio.file.Files.readAllBytes(localFile.toPath());
            }

        } catch (Exception ex) {
            logger.error("Failed loading {} from {}", type, dbPath, ex);
        }
        return null;
    }

    private String extractBucketName(String gsUri) {
        if (gsUri == null || !gsUri.startsWith("gs://")) return "vyaparsathi_s3_bucket";
        String bucket = gsUri.substring(5); // Remove gs://
        if (bucket.contains("/")) {
            bucket = bucket.split("/")[0]; // Get everything before the first slash
        }
        return bucket;
    }

    private String extractObjectPath(String gsUri) {
        if (gsUri == null || !gsUri.startsWith("gs://")) return gsUri;
        String withoutProtocol = gsUri.substring(5);
        int firstSlash = withoutProtocol.indexOf("/");
        if (firstSlash == -1) return "";
        return withoutProtocol.substring(firstSlash + 1);
    }

    // --- Formatting Utils (Kept as is) ---

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
        if (number == null || number.compareTo(ZERO) == 0) return "Zero Rupees";

        long whole = number.longValue();
        StringBuilder sb = new StringBuilder();

        if (whole > 0) {
            sb.append(convertToIndianWords(whole));
        } else {
            sb.append("Zero");
        }

        int paise = number.subtract(new BigDecimal(whole)).movePointRight(2).abs().intValue();
        if (paise > 0) {
            sb.append(" and ").append(convertToIndianWords(paise)).append(" Paise");
        }

        return sb.toString().trim().replaceAll("\\s+", " ") + " Rupees";
    }

    private static String convertToIndianWords(long n) {
        if (n == 0) return "";

        String[] units = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

        StringBuilder sb = new StringBuilder();

        if (n >= 10000000) {
            sb.append(convertToIndianWords(n / 10000000)).append(" Crore ");
            n %= 10000000;
        }
        if (n >= 100000) {
            sb.append(convertToIndianWords(n / 100000)).append(" Lakh ");
            n %= 100000;
        }
        if (n >= 1000) {
            sb.append(convertToIndianWords(n / 1000)).append(" Thousand ");
            n %= 1000;
        }
        if (n >= 100) {
            sb.append(units[(int)(n / 100)]).append(" Hundred ");
            n %= 100;
            if (n > 0) sb.append("and ");
        }
        if (n > 0) {
            if (n < 20) {
                sb.append(units[(int)n]).append(" ");
            } else {
                sb.append(tens[(int)(n / 10)]).append(" ");
                if (n % 10 > 0) {
                    sb.append(units[(int)(n % 10)]).append(" ");
                }
            }
        }
        return sb.toString();
    }

    public static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try { String color = hex.startsWith("#") ? hex : "#" + hex; return Color.decode(color); } catch (Exception e) { return fallback; }
    }

    public static PdfPCell createCell(String text, com.lowagie.text.Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(5); cell.setHorizontalAlignment(align); cell.setVerticalAlignment(Element.ALIGN_MIDDLE); return cell;
    }

    public static void addTotalRow(PdfPTable table, String label, String value, com.lowagie.text.Font font) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, font)); lCell.setBorder(Rectangle.NO_BORDER); table.addCell(lCell);
        PdfPCell vCell = new PdfPCell(new Phrase(value, font)); vCell.setBorder(Rectangle.NO_BORDER); vCell.setHorizontalAlignment(Element.ALIGN_RIGHT); table.addCell(vCell);
    }

    public static byte[] generateUPIDynamicQRCode(String upiId, String shopName, BigDecimal amount) {
        if (upiId == null || upiId.isBlank()) return null;
        try {
            String data = "upi://pay?pa=" + java.net.URLEncoder.encode(upiId.trim(), "UTF-8") +
                    "&pn=" + java.net.URLEncoder.encode(shopName != null ? shopName.trim() : "Shop", "UTF-8");
            if (amount != null && amount.compareTo(ZERO) > 0) {
                data += "&am=" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toString();
            }
            data += "&cu=INR";
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 150, 150);
            java.io.ByteArrayOutputStream pngOutputStream = new java.io.ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (Exception e) {
            logger.warn("Failed to generate UPI QR code for UPI ID: {}", upiId, e);
            return null;
        }
    }
}