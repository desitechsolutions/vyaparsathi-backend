package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ReceivingTicketAttachment extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_ticket_id", nullable = false)
    private ReceivingTicket receivingTicket;

    private String fileName;
    private String fileType;
    private String filePath; // Or store the file directly as a byte array using @Lob
}
