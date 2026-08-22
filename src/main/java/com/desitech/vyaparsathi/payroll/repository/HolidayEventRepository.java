package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.HolidayEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayEventRepository extends JpaRepository<HolidayEvent, Long> {
    List<HolidayEvent> findByCalendarIdAndHolidayDateBetween(Long calendarId, LocalDate startDate, LocalDate endDate);

    List<HolidayEvent> findByCalendarId(Long calendarId);

    long countByCalendarId(Long calendarId);
}
