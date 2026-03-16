package com.desitech.vyaparsathi.receiving.dto;

import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO used by {@code POST /api/receiving/receive-goods}.
 *
 * <p>Two usage modes:</p>
 * <ol>
 *   <li><b>Create mode</b> – supply only {@code purchaseOrderId} (+ optional metadata).
 *       The backend creates a new PENDING receiving record with placeholder items from the PO.</li>
 *   <li><b>Update mode</b> – supply {@code receivingId} together with the full
 *       {@code receivingItems} list. The backend updates the existing receiving record
 *       (recording actual received/damaged/rejected quantities and adjusting stock).</li>
 * </ol>
 */
@Data
public class CreateReceivingDto {

    /** ID of the existing Receiving record to update (update-mode only). */
    private Long receivingId;

    /** ID of the Purchase Order this receiving belongs to (required for create-mode). */
    private Long purchaseOrderId;

    /** Shop ID override. When absent the current tenant shop is used. */
    private Long shopId;

    /** Free-text notes for the receiving session. */
    private String notes;

    /** Who is performing the receiving (displayed on the receiving record). */
    private String receivedBy;

    @PastOrPresent(message = "Received date cannot be in the future")
    private LocalDateTime receivedDate;

    /** Line-item quantities + industry-specific fields (update-mode). */
    private List<ReceivingItemDto> receivingItems;
}
