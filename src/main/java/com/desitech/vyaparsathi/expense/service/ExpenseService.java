package com.desitech.vyaparsathi.expense.service;

import com.desitech.vyaparsathi.changelog.service.ChangeLogService;
import com.desitech.vyaparsathi.changelog.model.ChangeLogOperation;
import com.desitech.vyaparsathi.expense.dto.ExpenseDto;
import com.desitech.vyaparsathi.expense.dto.UpdateExpenseDto;
import com.desitech.vyaparsathi.expense.entity.Expense;
import com.desitech.vyaparsathi.expense.mapper.ExpenseMapper;
import com.desitech.vyaparsathi.expense.repository.ExpenseRepository;
import com.desitech.vyaparsathi.expense.validation.ExpenseTypeValidator;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository repository;

    @Autowired
    private ExpenseMapper mapper;

    @Autowired
    private ChangeLogService changeLogService;

    @Transactional
    public ExpenseDto create(@Valid ExpenseDto dto) {
        // Validate that this is not an inventory/stock purchase
        ExpenseTypeValidator.isValidOperationalExpense(dto.getType());
        
        Expense expense = mapper.toEntity(dto);
        repository.save(expense);
    changeLogService.append("EXPENSE", expense.getId(), ChangeLogOperation.CREATE, expense, "LOCAL_DEVICE");
        return mapper.toDto(expense);
    }

    public Page<ExpenseDto> list(Pageable pageable, Long shopId) {
        return repository.findByShopIdAndNotDeleted(shopId, pageable).map(mapper::toDto);
    }

    public ExpenseDto get(Long id) {
        Expense expense = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Expense with id " + id + " not found or is deleted"));
        return mapper.toDto(expense);
    }

    @Transactional
    public ExpenseDto update(Long id, @Valid UpdateExpenseDto dto) {
        Expense expense = repository.findByIdAndDeletedFalse(id)
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
        if (expense.isDeleted()) {
            return;
        }
        expense.setDeleted(true);
        repository.save(expense);
    changeLogService.append("EXPENSE", id, ChangeLogOperation.DELETE, null, "LOCAL_DEVICE");
    }
}
