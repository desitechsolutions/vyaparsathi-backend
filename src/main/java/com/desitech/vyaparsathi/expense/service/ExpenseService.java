package com.desitech.vyaparsathi.expense.service;

import com.desitech.vyaparsathi.changelog.service.ChangeLogService;
import com.desitech.vyaparsathi.changelog.model.ChangeLogOperation;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.expense.dto.ExpenseDto;
import com.desitech.vyaparsathi.expense.dto.UpdateExpenseDto;
import com.desitech.vyaparsathi.expense.entity.Expense;
import com.desitech.vyaparsathi.expense.mapper.ExpenseMapper;
import com.desitech.vyaparsathi.expense.repository.ExpenseRepository;
import com.desitech.vyaparsathi.expense.validation.ExpenseTypeValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository repository;

    @Autowired
    private ExpenseMapper mapper;

    @Autowired
    private ChangeLogService changeLogService;

    @Autowired(required = false)
    private ExpensePolicyService policyService;

    @Autowired(required = false)
    private ExpenseApprovalService approvalService;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Transactional
    public ExpenseDto create(@Valid ExpenseDto dto) {
        // Validate that this is not an inventory/stock purchase
        ExpenseTypeValidator.isValidOperationalExpense(dto.getType());
        
        Expense expense = mapper.toEntity(dto);
        repository.save(expense);
    changeLogService.append("EXPENSE", expense.getId(), ChangeLogOperation.CREATE, expense, "LOCAL_DEVICE");
        return mapper.toDto(expense);
    }

    public Page<ExpenseDto> list(Pageable pageable) {
        Long currentShopId = TenantContext.getCurrentShopId();
        if (currentShopId == null) {
            throw new IllegalStateException("No shop context available");
        }
        return repository.findByShopIdAndNotDeleted(currentShopId, pageable).map(mapper::toDto);
    }
    public ExpenseDto get(Long id) {
        Expense expense = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Expense with id " + id + " not found or is deleted"));
        return mapper.toDto(expense);
    }

    @Transactional
    public ExpenseDto update(Long id, @Valid UpdateExpenseDto dto) {
        Expense expense = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Expense with id " + id + " not found or is deleted"));
        
        // Validate expense type if it's being changed
        if (dto.getType() != null) {
            ExpenseTypeValidator.isValidOperationalExpense(dto.getType());
        }
        
        mapper.updateEntityFromDto(dto, expense);
        repository.save(expense);
    changeLogService.append("EXPENSE", expense.getId(), ChangeLogOperation.UPDATE, expense, "LOCAL_DEVICE");
        return mapper.toDto(expense);
    }

    @Transactional
    public void delete(Long id) {
        Expense expense = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Expense with id " + id + " not found"));
        // Check if already deleted
        if (Boolean.TRUE.equals(expense.getIsDeleted())) {
            return;
        }
        expense.setIsDeleted(true);
        repository.save(expense);
    changeLogService.append("EXPENSE", id, ChangeLogOperation.DELETE, null, "LOCAL_DEVICE");
    }

    // ── Enterprise: Policy Validation ────────────────────────────────

    @Transactional
    public ExpenseDto createWithPolicyValidation(Long shopId, String userId, ExpenseDto request) {
        if (policyService == null) {
            return create(request); // Fallback to legacy
        }

        validateExpenseRequest(request);
        Expense expense = mapper.toEntity(request);
        com.desitech.vyaparsathi.shop.entity.Shop shop = new com.desitech.vyaparsathi.shop.entity.Shop();
        shop.setId(shopId);
        expense.setShop(shop);
        expense.setEmployeeId(userId);
        expense.setStatus(Expense.ExpenseStatus.DRAFT);
        if (expense.getCurrency() == null) expense.setCurrency("INR");

        repository.save(expense);

        // Validate against policies
        List<ExpensePolicyService.PolicyViolation> violations = policyService.validateExpense(shopId, expense);
        if (!violations.isEmpty()) {
            storeViolations(expense, violations);
            boolean autoRejected = policyService.enforcePolicy(expense, violations);
            if (autoRejected) {
                repository.save(expense);
                log.warn("[Expense] Expense {} auto-rejected due to policy violation", expense.getId());
            }
        }

        changeLogService.append("EXPENSE", expense.getId(), ChangeLogOperation.CREATE, expense, "POLICY_ENGINE");
        return mapper.toDto(expense);
    }

    // ── Enterprise: Approval Workflow ────────────────────────────────

    @Transactional
    public void submitForApproval(Long shopId, Long expenseId, List<String> approverIds) {
        if (approvalService == null) {
            throw new RuntimeException("Approval service not configured");
        }

        Expense expense = repository.findByIdAndIsDeletedFalse(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Expense not found: " + expenseId));

        if (!expense.getStatus().equals(Expense.ExpenseStatus.DRAFT)) {
            throw new RuntimeException("Can only submit DRAFT expenses");
        }

        approvalService.initializeApprovalChain(expense, approverIds);
        expense.setStatus(Expense.ExpenseStatus.SUBMITTED);
        expense.setSubmissionDate(LocalDateTime.now());
        repository.save(expense);

        log.info("[Expense] Submitted expense {} for approval", expenseId);
        changeLogService.append("EXPENSE", expenseId, ChangeLogOperation.UPDATE, expense, "APPROVAL_ENGINE");
    }

    // ── Enterprise: Analytics ────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ExpenseDto> getExpensesByStatus(Long shopId, Expense.ExpenseStatus status, Pageable pageable) {
        return repository.findByShopIdAndStatus(shopId, status, pageable)
                .map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseDto> getEmployeeExpenses(Long shopId, String employeeId, Pageable pageable) {
        return repository.findByShopIdAndEmployeeId(shopId, employeeId, pageable)
                .map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public long getPendingApprovalCount(Long shopId) {
        return repository.countPendingApprovals(shopId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCategorySpending(Long shopId, Long categoryId, LocalDate startDate, LocalDate endDate) {
        return repository.sumByCategoryAndPeriod(shopId, categoryId, startDate, endDate)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllCategorySpending(Long shopId, LocalDate startDate, LocalDate endDate) {
        List<Expense> expenses = repository.findByShopIdAndNotDeleted(shopId, Pageable.unpaged()).getContent();
        return expenses.stream()
                .filter(e -> e.getExpenseDate().isAfter(startDate.minusDays(1))
                        && e.getExpenseDate().isBefore(endDate.plusDays(1)))
                .collect(Collectors.groupingBy(
                        e -> e.getExpenseCategoryId(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Expense::getAmount,
                                BigDecimal::add
                        )
                ))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("categoryId", entry.getKey());
                    m.put("amount", entry.getValue().doubleValue());
                    m.put("startDate", startDate);
                    m.put("endDate", endDate);
                    return m;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getTotalExpensesCount(Long shopId) {
        return repository.findByShopIdAndNotDeleted(shopId, Pageable.unpaged())
                .getTotalElements();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void validateExpenseRequest(ExpenseDto request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (request.getExpenseDate() == null) {
            throw new IllegalArgumentException("Expense date is required");
        }
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }
    }

    private void storeViolations(Expense expense, List<ExpensePolicyService.PolicyViolation> violations) {
        if (objectMapper == null) return;
        try {
            List<String> violationMessages = violations.stream()
                    .map(v -> v.policyName + ": " + v.reason)
                    .toList();
            expense.setPolicyViolationsJson(objectMapper.writeValueAsString(violationMessages));
        } catch (Exception e) {
            log.error("Failed to store violations", e);
        }
    }
}
