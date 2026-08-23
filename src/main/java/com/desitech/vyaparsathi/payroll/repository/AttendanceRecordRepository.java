package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.AttendanceRecord;
import com.desitech.vyaparsathi.payroll.enums.AttendanceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long employeeId, LocalDate startDate, LocalDate endDate);

    List<AttendanceRecord> findByEmployeeIdAndAttendanceDateAndAttendanceType(
            Long employeeId, LocalDate attendanceDate, AttendanceType attendanceType);

    long countByEmployeeIdAndAttendanceDateBetweenAndAttendanceType(
            Long employeeId, LocalDate startDate, LocalDate endDate, AttendanceType attendanceType);

    List<AttendanceRecord> findByShopIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long shopId, LocalDate startDate, LocalDate endDate);

    long countByEmployeeIdAndAttendanceDateBetween(
            Long employeeId, LocalDate startDate, LocalDate endDate);
}
