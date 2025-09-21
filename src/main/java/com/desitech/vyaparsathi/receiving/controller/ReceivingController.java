package com.desitech.vyaparsathi.receiving.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.receiving.dto.CreateReceivingDto;
import com.desitech.vyaparsathi.receiving.dto.ReceivingDto;
import com.desitech.vyaparsathi.receiving.dto.ReceivingTicketDTO;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.service.ReceivingService;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.extern.java.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/receiving")
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
public class ReceivingController {

    private static final Logger log = LoggerFactory.getLogger(ReceivingController.class);
    @Autowired
    private ReceivingService receivingService;

    @GetMapping
    public ResponseEntity<Page<ReceivingDto>> getAllReceivings(Pageable pageable) {
        Page<ReceivingDto> receivings = receivingService.getAllReceivings(pageable);
        return ResponseEntity.ok(receivings);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceivingDto> getReceivingById(@PathVariable Long id) {
        return ResponseEntity.ok(receivingService.getReceivingById(id));
    }
    @GetMapping("/by-po/{poId}")
    public ResponseEntity<List<ReceivingDto>> getReceivingByPurchaseOrderId(@PathVariable Long poId) {
        return ResponseEntity.ok(receivingService.getAllByPurchaseOrderId(poId));
    }
    @GetMapping("/by-po-number/{poNumber}")
    public ResponseEntity<List<ReceivingDto>> getReceivingByPoNumber(@PathVariable String poNumber) {
        log.info("searching Receiving with PO Number: {}", poNumber);
        return ResponseEntity.ok(receivingService.getAllByPoNumber(poNumber));
    }

    @PostMapping
    public ResponseEntity<ReceivingDto> createReceiving(@Valid @RequestBody ReceivingDto receivingDto) {
        ReceivingDto receiving = receivingService.createReceiving(receivingDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(receiving);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReceivingDto> updateReceiving(
            @PathVariable Long id,
            @Valid @RequestBody ReceivingDto receivingDto) {

        return receivingService.updateReceiving(id, receivingDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReceiving(@PathVariable Long id) {
        receivingService.deleteReceiving(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tickets")
    public ResponseEntity<ReceivingTicket> createReceivingTicket(@Valid @RequestBody ReceivingTicketDTO receivingTicketDTO) {
        ReceivingTicket ticket = receivingService.createReceivingTicket(receivingTicketDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    @GetMapping("/receiving-tickets/{id}")
    public ResponseEntity<List<ReceivingTicket>> getReceivingTicketByReceivingId(@PathVariable Long id) {
        return ResponseEntity.ok(receivingService.getReceivingTicketByReceivingId(id));
    }
    @GetMapping("/tickets/{id}")
    public ResponseEntity<ReceivingTicket> getReceivingTicketById(@PathVariable Long id) {
        return ResponseEntity.of(receivingService.getReceivingTicketById(id));
    }

    @PostMapping("/receive-goods")
    public ResponseEntity<ReceivingDto> receiveGoods(@Valid @RequestBody CreateReceivingDto createReceivingDto) {
        ReceivingDto receiving = receivingService.createInitialReceivingRecord(createReceivingDto);
        return ResponseEntity.ok(receiving);
    }

    // Added: Endpoint for updating ReceivingTicket (basic, expand as needed)
    @PutMapping("/tickets/{id}")
    public ResponseEntity<ReceivingTicket> updateReceivingTicket(@PathVariable Long id, @Valid @RequestBody ReceivingTicketDTO receivingTicketDTO) {
        return receivingService.updateReceivingTicket(id, receivingTicketDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Added: Endpoint for deleting ReceivingTicket
    @DeleteMapping("/tickets/{id}")
    public ResponseEntity<Void> deleteReceivingTicket(@PathVariable Long id) {
        receivingService.deleteReceivingTicket(id);
        return ResponseEntity.noContent().build();
    }

    // Added: Endpoint for attaching files to ReceivingTicket (using multipart)
    @PostMapping("/tickets/{id}/attachments")
    public ResponseEntity<ReceivingTicket> addAttachmentToTicket(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        ReceivingTicket updatedTicket = receivingService.addAttachmentToTicket(id, file);
        return ResponseEntity.ok(updatedTicket);
    }
}