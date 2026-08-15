package com.desitech.vyaparsathi.document.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Small utility over ZXing to produce PNG QR codes for the enterprise
 * document renderer. Used for the UPI payment QR and (later) the
 * IRP-signed invoice QR.
 */
@Service
public class QrCodeService {

    /** Builds a QR-code PNG of {@code content} at {@code size × size} px. */
    public byte[] encode(String content, int size) {
        if (content == null || content.isBlank()) return null;
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException | java.io.IOException e) {
            return null;
        }
    }

    /**
     * Builds a UPI payment URI per NPCI spec (upi://pay?pa=&pn=&am=&cu=INR)
     * from the components a shop already stores. Amount is optional —
     * the customer's UPI app will prompt when omitted.
     */
    public String upiUri(String upiId, String payeeName, java.math.BigDecimal amount, String reference) {
        if (upiId == null || upiId.isBlank()) return null;
        StringBuilder sb = new StringBuilder("upi://pay?");
        sb.append("pa=").append(url(upiId));
        if (payeeName != null && !payeeName.isBlank())
            sb.append("&pn=").append(url(payeeName));
        if (amount != null && amount.signum() > 0)
            sb.append("&am=").append(amount.toPlainString());
        sb.append("&cu=INR");
        if (reference != null && !reference.isBlank())
            sb.append("&tr=").append(url(reference));
        return sb.toString();
    }

    private String url(String v) {
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }
}
