package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cold-chain temperature/condition reading — captured at receipt time or by
 * on-going sensors. {@code withinSpec = false} rows should trigger a review
 * before the receiving is confirmed.
 */
@Entity
@Table(name = "temperature_log")
@Getter
@Setter
@NoArgsConstructor
public class TemperatureLog extends ShopAwareEntity {

    @Column(name = "receiving_id")
    private Long receivingId;

    @Column(name = "reading_at", nullable = false)
    private LocalDateTime readingAt = LocalDateTime.now();

    @Column(name = "temperature_c", nullable = false, precision = 6, scale = 2)
    private BigDecimal temperatureC;

    @Column(name = "humidity_pct", precision = 5, scale = 2)
    private BigDecimal humidityPct;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "within_spec", nullable = false)
    private boolean withinSpec = true;

    @Column(name = "logged_by", length = 100)
    private String loggedBy;

    @Column(name = "notes", length = 500)
    private String notes;
}
