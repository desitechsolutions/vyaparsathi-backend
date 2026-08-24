package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.payroll.dto.HolidayCalendarDto;
import com.desitech.vyaparsathi.payroll.dto.HolidayEventDto;
import com.desitech.vyaparsathi.payroll.entity.HolidayCalendar;
import com.desitech.vyaparsathi.payroll.entity.HolidayEvent;
import com.desitech.vyaparsathi.payroll.repository.HolidayCalendarRepository;
import com.desitech.vyaparsathi.payroll.repository.HolidayEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HolidayCalendarService {

    @Autowired
    private HolidayCalendarRepository holidayCalendarRepository;

    @Autowired
    private HolidayEventRepository holidayEventRepository;

    @Transactional
    public HolidayCalendarDto createHolidayCalendar(Integer year, String weeklyOffDays) {
        Long shopId = TenantContext.getCurrentShopId();

        HolidayCalendar calendar = new HolidayCalendar();
        calendar.setYear(year);
        calendar.setName("Calendar " + year);
        calendar.setTotalWorkingDays(252);
        calendar.setWeeklyOffDays(weeklyOffDays);

        holidayCalendarRepository.save(calendar);

        return HolidayCalendarDto.builder()
                .id(calendar.getId())
                .year(year)
                .name(calendar.getName())
                .totalWorkingDays(calendar.getTotalWorkingDays())
                .weeklyOffDays(weeklyOffDays)
                .build();
    }

    @Transactional
    public HolidayEventDto addHolidayEvent(Long calendarId, LocalDate date, String name, String type) {
        HolidayCalendar calendar = holidayCalendarRepository.findById(calendarId)
                .orElseThrow(() -> new EntityNotFoundAppException("HolidayCalendar", calendarId));

        HolidayEvent event = HolidayEvent.builder()
                .calendar(calendar)
                .holidayDate(date)
                .eventName(name)
                .eventType(type)
                .build();

        holidayEventRepository.save(event);

        return HolidayEventDto.builder()
                .id(event.getId())
                .holidayDate(date)
                .eventName(name)
                .eventType(type)
                .build();
    }

    public List<HolidayEventDto> getHolidaysForMonth(Integer year, Integer month) {
        Long shopId = TenantContext.getCurrentShopId();
        HolidayCalendar calendar = holidayCalendarRepository.findByShopIdAndYear(shopId, year)
                .orElseThrow(() -> new EntityNotFoundAppException("HolidayCalendar", null));

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return holidayEventRepository.findByCalendarIdAndHolidayDateBetween(calendar.getId(), startDate, endDate)
                .stream()
                .map(he -> HolidayEventDto.builder()
                        .id(he.getId())
                        .holidayDate(he.getHolidayDate())
                        .eventName(he.getEventName())
                        .eventType(he.getEventType())
                        .description(he.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    public Integer calculateWorkingDays(Long calendarId, Integer month, Integer year) {
        HolidayCalendar calendar = holidayCalendarRepository.findById(calendarId)
                .orElseThrow(() -> new EntityNotFoundAppException("HolidayCalendar", calendarId));

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        int totalDays = endDate.getDayOfMonth();
        int weekendCount = 0;
        int holidayCount = 0;

        List<String> weeklyOffs = Arrays.asList(calendar.getWeeklyOffDays().split(","));

        // Count weekends
        for (int day = 1; day <= totalDays; day++) {
            LocalDate date = LocalDate.of(year, month, day);
            DayOfWeek dayOfWeek = date.getDayOfWeek();

            if (weeklyOffs.contains(dayOfWeek.name())) {
                weekendCount++;
            }
        }

        // Count holidays
        List<HolidayEvent> holidays = holidayEventRepository
                .findByCalendarIdAndHolidayDateBetween(calendarId, startDate, endDate);
        holidayCount = holidays.size();

        return totalDays - weekendCount - holidayCount;
    }

    /**
     * Convenience method for PayrollCalculationEngine — resolves calendar by shopId automatically.
     * Falls back to weekend-only calculation if no holiday calendar is configured for the year.
     */
    public Integer calculateWorkingDaysForShop(Long shopId, Integer month, Integer year) {
        HolidayCalendar calendar = holidayCalendarRepository.findByShopIdAndYear(shopId, year).orElse(null);
        if (calendar == null) {
            // No calendar configured — use weekend-only calculation
            java.time.YearMonth ym = java.time.YearMonth.of(year, month);
            int totalDays = ym.lengthOfMonth();
            int weekends = 0;
            for (int d = 1; d <= totalDays; d++) {
                java.time.DayOfWeek dow = java.time.LocalDate.of(year, month, d).getDayOfWeek();
                if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) weekends++;
            }
            return totalDays - weekends;
        }
        return calculateWorkingDays(calendar.getId(), month, year);
    }
}
