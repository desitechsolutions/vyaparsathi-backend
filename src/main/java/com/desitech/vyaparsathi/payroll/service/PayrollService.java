package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.audit.helper.AuditHelper;
import com.desitech.vyaparsathi.changelog.model.ChangeLogOperation;
import com.desitech.vyaparsathi.changelog.service.ChangeLogService;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.event.NotificationEvent;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.payroll.dto.*;
import com.desitech.vyaparsathi.payroll.entity.PayrollRecord;
import com.desitech.vyaparsathi.payroll.entity.Staff;
import com.desitech.vyaparsathi.payroll.enums.PayrollStatus;
import com.desitech.vyaparsathi.payroll.mapper.PayrollMapper;
import com.desitech.vyaparsathi.payroll.mapper.StaffMapper;
import com.desitech.vyaparsathi.payroll.repository.PayrollRecordRepository;
import com.desitech.vyaparsathi.payroll.repository.StaffRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PayrollService {

    @Autowired private StaffRepository staffRepository;
    @Autowired private PayrollRecordRepository payrollRecordRepository;
    @Autowired private PayrollMapper payrollMapper;
    @Autowired private StaffMapper staffMapper;
    @Autowired private ChangeLogService changeLogService;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired
    private AuditHelper auditHelper;

    // --- STAFF MANAGEMENT ---

    @Transactional
    public StaffDto addStaff(@Valid StaffDto dto) {
        Shop shop = getCurrentShop();
        Staff staff = staffMapper.toEntity(dto);
        staff.setShop(shop);
        staff.setAdvanceBalance(BigDecimal.ZERO);
        staff.setActive(true);

        staffRepository.save(staff);
        changeLogService.append("STAFF", staff.getId(), ChangeLogOperation.CREATE, staff, "LOCAL_DEVICE");
        return staffMapper.toDto(staff);
    }

    @Transactional
    public StaffDto updateStaff(Long id, @Valid StaffDto dto) {
        Staff staff = findStaffById(id);
        staffMapper.updateEntityFromDto(dto, staff);

        staffRepository.save(staff);
        changeLogService.append("STAFF", id, ChangeLogOperation.UPDATE, staff, "LOCAL_DEVICE");
        return staffMapper.toDto(staff);
    }

    @Transactional
    public void deleteStaff(Long id) {
        Staff staff = findStaffById(id);
        staff.setActive(false); // Soft delete
        staffRepository.save(staff);
        changeLogService.append("STAFF", id, ChangeLogOperation.DELETE, null, "LOCAL_DEVICE");
    }

    // --- ADVANCE MANAGEMENT ---

    @Transactional
    public void issueAdvance(Long staffId, BigDecimal amount, String remarks) {
        Staff staff = findStaffById(staffId);
        staff.setAdvanceBalance(staff.getAdvanceBalance().add(amount));
        staffRepository.save(staff);

        changeLogService.append("STAFF_ADVANCE", staffId, ChangeLogOperation.UPDATE, "Issued: " + amount, "LOCAL_DEVICE");

        eventPublisher.publishEvent(new NotificationEvent(
                this,
                "payment",
                "Salary Paid",
                "₹" + amount + " paid to " + staff.getName(),
                "admin@shop.com",
                "/payroll/history",
                "low"
        ));
    }

    // --- PAYROLL PROCESSING ---

    @Transactional
    public PayrollResponseDto processSalary(@Valid PayrollRequestDto dto) {
        Staff staff = findStaffById(dto.getStaffId());
        Long shopId = TenantContext.getCurrentShopId();

        // 1. Check for Duplicate Payment (Prevention)
        if (payrollRecordRepository.existsByStaffIdAndSalaryMonthAndSalaryYearAndShopId(
                staff.getId(), dto.getSalaryMonth(), dto.getSalaryYear(), shopId)) {
            throw new IllegalStateException("Salary already processed for " + dto.getSalaryMonth() + " " + dto.getSalaryYear());
        }

        // 2. Perform Financial Calculation
        BigDecimal base = staff.getBaseSalary();
        BigDecimal netAmount = base.add(dto.getBonus())
                .subtract(dto.getDeductions())
                .subtract(dto.getAdvanceDeduction());

        // 3. Create Entity
        PayrollRecord record = new PayrollRecord();
        record.setStaff(staff);
        record.setShop(getCurrentShop());
        record.setSalaryMonth(dto.getSalaryMonth());
        record.setSalaryYear(dto.getSalaryYear());
        record.setBaseSalaryAtTime(base);
        record.setBonus(dto.getBonus());
        record.setDeductions(dto.getDeductions());
        record.setAdvanceDeduction(dto.getAdvanceDeduction());
        record.setNetAmount(netAmount);
        record.setPaymentDate(LocalDate.now());
        record.setStatus(PayrollStatus.PAID);
        record.setPaymentMode(dto.getPaymentMode());
        record.setRemarks(dto.getRemarks());

        // 4. Recovery: Deduct from Staff Advance Balance
        if (dto.getAdvanceDeduction().compareTo(BigDecimal.ZERO) > 0) {
            staff.setAdvanceBalance(staff.getAdvanceBalance().subtract(dto.getAdvanceDeduction()));
            staffRepository.save(staff);
        }

        payrollRecordRepository.save(record);

        // 5. Audit and Notify
        auditHelper.log("PAYROLL", "Payroll Processing", record.getId().toString(), record.getStaff().getName()+" - "+ record.getSalaryMonth() +" SALARY");
        changeLogService.append("PAYROLL", record.getId(), ChangeLogOperation.CREATE, record, "LOCAL_DEVICE");

        eventPublisher.publishEvent(new NotificationEvent(
                this,
                "payment",
                "Salary Paid",
                "₹" + netAmount + " paid to " + staff.getName(),
                "admin@shop.com",
                "/payroll/history",
                "low"
        ));

        return payrollMapper.toDto(record);
    }

    // --- QUERIES ---

    // Update the method signature to include month and year
    public Page<StaffResponseDto> listStaff(String month, Integer year, Pageable pageable) {
        Long shopId = TenantContext.getCurrentShopId();

        // 1. Get the page of staff as you did before
        Page<Staff> staffPage = staffRepository.findByShopIdAndActiveTrue(shopId, pageable);

        // 2. Map and Check Status in one go
        return staffPage.map(staff -> {
            // Map base fields
            StaffResponseDto dto = staffMapper.toResponseDto(staff);

            // 3. Check database if a payment record exists for THIS specific month/year
            boolean isPaid = payrollRecordRepository.existsByStaffIdAndSalaryMonthAndSalaryYearAndShopId(
                    staff.getId(), month, year, shopId
            );

            dto.setPaidInCurrentPeriod(isPaid);
            return dto;
        });
    }

    public Page<PayrollResponseDto> listPayments(Long staffId, Pageable pageable) {
        return payrollRecordRepository.findByShopIdAndStaffId(TenantContext.getCurrentShopId(), staffId, pageable)
                .map(payrollMapper::toDto);
    }

    // --- HELPERS ---

    private Staff findStaffById(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Staff", id));
    }

    private Shop getCurrentShop() {
        return shopRepository.findById(TenantContext.getCurrentShopId())
                .orElseThrow(() -> new EntityNotFoundAppException("Shop", TenantContext.getCurrentShopId()));
    }

    // --- READ OPERATIONS ---

    public StaffDto getStaff(Long id) {
        // We use findByIdAndActiveTrue to ensure we don't return soft-deleted staff
        Staff staff = staffRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Staff", id));
        return staffMapper.toDto(staff);
    }

    public List<StaffDto> getAllActiveStaffForShop() {
        // Useful for population of dropdowns in the "Pay Salary" modal
        return staffRepository.findByShopIdAndActiveTrue(TenantContext.getCurrentShopId())
                .stream()
                .map(staffMapper::toDto)
                .toList();
    }
}