package com.desitech.vyaparsathi.support.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "support_chats")
@Data
public class ChatMessageEntity extends ShopAwareEntity {

    private String senderName;

    @Column(columnDefinition = "TEXT")
    private String message;

    private boolean isFromAdmin;

    private boolean isReadByAdmin = false;
}