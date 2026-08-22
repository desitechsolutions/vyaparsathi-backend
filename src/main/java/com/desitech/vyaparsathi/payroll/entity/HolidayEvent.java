package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "holiday_events", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"calendar_id", "holiday_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayEvent extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    private HolidayCalendar calendar;

    @Column(nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 200)
    private String eventName;

    @Column(nullable = false, length = 50)
    private String eventType; // NATIONAL, REGIONAL, OPTIONAL, RESTRICTED

    @Column(columnDefinition = "TEXT")
    private String description;
}
