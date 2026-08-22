package com.desitech.vyaparsathi.payroll.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "holiday_calendars", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"shop_id", "year"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayCalendar extends ShopAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer totalWorkingDays;

    @Column(nullable = false, length = 100)
    private String weeklyOffDays; // e.g., "SUNDAY,SATURDAY"

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HolidayEvent> events;
}
