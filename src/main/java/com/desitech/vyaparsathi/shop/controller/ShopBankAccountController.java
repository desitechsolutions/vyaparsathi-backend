package com.desitech.vyaparsathi.shop.controller;

import com.desitech.vyaparsathi.shop.dto.ShopBankAccountDto;
import com.desitech.vyaparsathi.shop.service.ShopBankAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shop/bank-accounts")
public class ShopBankAccountController {

    private final ShopBankAccountService service;

    public ShopBankAccountController(ShopBankAccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<ShopBankAccountDto> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<ShopBankAccountDto> create(@Valid @RequestBody ShopBankAccountDto dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShopBankAccountDto> update(@PathVariable Long id, @Valid @RequestBody ShopBankAccountDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<ShopBankAccountDto> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(service.setDefault(id));
    }
}
