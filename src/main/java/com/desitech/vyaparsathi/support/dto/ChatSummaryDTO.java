package com.desitech.vyaparsathi.support.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatSummaryDTO {
    private Long shopId;
    private String shopName;
    private String lastMessage;
    private LocalDateTime timestamp;
    private boolean hasUnread;
}