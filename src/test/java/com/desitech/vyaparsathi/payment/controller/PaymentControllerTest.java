package com.desitech.vyaparsathi.payment.controller;

import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentControllerTest {
    @Mock
    private PaymentService paymentService;
    @InjectMocks
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createPayment_ShouldReturnSavedDto() {
        PaymentDto dto = new PaymentDto();
        when(paymentService.createPayment(dto)).thenReturn(dto);
        ResponseEntity<PaymentDto> response = paymentController.createPayment(dto);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(dto, response.getBody());
    }

    @Test
    void getPayments_BySourceTypeAndSourceId() {
        PaymentDto dto = new PaymentDto();
        // Wrap the list in a Page object
        Page<PaymentDto> page = new PageImpl<>(List.of(dto));

        // Update stub to match the Pageable parameter (any() or specific PageRequest)
        when(paymentService.getPaymentsBySource(eq(PaymentSourceType.SALE), eq(1L), any(Pageable.class)))
                .thenReturn(page);

        // Call with default page/size or the ones defined in your controller defaults
        ResponseEntity<Page<PaymentDto>> response = paymentController.getPayments(PaymentSourceType.SALE, 1L, null, null, 0, 20);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void getPayments_BySupplierId() {
        PaymentDto dto = new PaymentDto();
        Page<PaymentDto> page = new PageImpl<>(List.of(dto));

        when(paymentService.getPaymentsBySupplier(eq(2L), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<PaymentDto>> response = paymentController.getPayments(null, null, 2L, null, 0, 20);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().getContent().size());
    }

    @Test
    void getPayments_ByCustomerId() {
        PaymentDto dto = new PaymentDto();
        Page<PaymentDto> page = new PageImpl<>(List.of(dto));

        when(paymentService.getPaymentsByCustomer(eq(3L), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<PaymentDto>> response = paymentController.getPayments(null, null, null, 3L, 0, 20);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().getContent().size());
    }

    @Test
    void getPayments_BadRequest() {
        // Note the change in return type to Page<PaymentDto>
        ResponseEntity<Page<PaymentDto>> response = paymentController.getPayments(null, null, null, null, 0, 20);
        assertEquals(400, response.getStatusCodeValue());
    }
    @Test
    void getPayment_Found() {
        PaymentDto dto = new PaymentDto();
        when(paymentService.getPayment(1L)).thenReturn(Optional.of(dto));
        ResponseEntity<PaymentDto> response = paymentController.getPayment(1L);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(dto, response.getBody());
    }

    @Test
    void getPayment_NotFound() {
        when(paymentService.getPayment(1L)).thenReturn(Optional.empty());
        ResponseEntity<PaymentDto> response = paymentController.getPayment(1L);
        assertEquals(404, response.getStatusCodeValue());
    }
}
