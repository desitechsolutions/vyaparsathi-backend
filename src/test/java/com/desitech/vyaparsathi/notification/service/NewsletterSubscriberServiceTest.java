package com.desitech.vyaparsathi.notification.service;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.notification.dto.NewsletterStatsDto;
import com.desitech.vyaparsathi.notification.dto.NewsletterSubscribeRequest;
import com.desitech.vyaparsathi.notification.entity.NewsletterSubscriber;
import com.desitech.vyaparsathi.notification.repository.NewsletterSubscriberRepository;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NewsletterSubscriberServiceTest {

    @Mock
    private NewsletterSubscriberRepository repository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NewsletterSubscriberService service;

    private NewsletterSubscribeRequest request;
    private NewsletterSubscriber activeSubscriber;
    private NewsletterSubscriber inactiveSubscriber;

    @BeforeEach
    void setUp() {
        request = new NewsletterSubscribeRequest();
        request.setEmail("test@example.com");
        request.setSource("Footer");

        activeSubscriber = new NewsletterSubscriber();
        activeSubscriber.setId(1L);
        activeSubscriber.setEmail("test@example.com");
        activeSubscriber.setActive(true);
        activeSubscriber.setSource("Footer");
        activeSubscriber.setUnsubscribeToken("token-123");

        inactiveSubscriber = new NewsletterSubscriber();
        inactiveSubscriber.setId(2L);
        inactiveSubscriber.setEmail("inactive@example.com");
        inactiveSubscriber.setActive(false);
        inactiveSubscriber.setSource("Pricing Page");
        inactiveSubscriber.setUnsubscribeToken("token-456");
    }

    @Test
    @DisplayName("Should successfully subscribe a new email and send welcome email")
    void subscribe_NewEmail_Success() throws MessagingException {
        when(repository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(NewsletterSubscriber.class))).thenReturn(activeSubscriber);

        String message = service.subscribe(request);

        assertEquals("Successfully subscribed to our newsletter!", message);
        verify(repository, times(1)).save(any(NewsletterSubscriber.class));
        verify(emailService, times(1)).sendEmail(eq("test@example.com"), eq("Welcome to VyaparSathi"), anyString());
    }

    @Test
    @DisplayName("Should return a friendly message if email is already active")
    void subscribe_AlreadyActive_ReturnsFriendlyMessage() {
        when(repository.findByEmail("test@example.com")).thenReturn(Optional.of(activeSubscriber));

        String message = service.subscribe(request);

        assertEquals("You are already subscribed to our newsletter!", message);
        verify(repository, never()).save(any());
        try {
            verify(emailService, never()).sendEmail(any(), any(), any());
        } catch (MessagingException e) {
            fail("Should not reach here");
        }
    }

    @Test
    @DisplayName("Should reactivate an existing inactive subscription and send welcome email")
    void subscribe_InactiveEmail_ReactivatesAndSendsEmail() throws MessagingException {
        when(repository.findByEmail("test@example.com")).thenReturn(Optional.of(inactiveSubscriber));
        when(repository.save(any(NewsletterSubscriber.class))).thenReturn(inactiveSubscriber);

        String message = service.subscribe(request);

        assertEquals("Successfully resubscribed to our newsletter!", message);
        assertTrue(inactiveSubscriber.isActive());
        assertNull(inactiveSubscriber.getUnsubscribedAt());
        verify(repository, times(1)).save(inactiveSubscriber);
        verify(emailService, times(1)).sendEmail(eq("inactive@example.com"), eq("Welcome to VyaparSathi"), anyString());
    }

    @Test
    @DisplayName("Should successfully unsubscribe using a valid token")
    void unsubscribe_ValidToken_Success() {
        when(repository.findByUnsubscribeToken("token-123")).thenReturn(Optional.of(activeSubscriber));
        when(repository.save(any(NewsletterSubscriber.class))).thenReturn(activeSubscriber);

        String email = service.unsubscribe("token-123");

        assertEquals("test@example.com", email);
        assertFalse(activeSubscriber.isActive());
        assertNotNull(activeSubscriber.getUnsubscribedAt());
        verify(repository, times(1)).save(activeSubscriber);
    }

    @Test
    @DisplayName("Should throw exception when unsubscribing with an invalid token")
    void unsubscribe_InvalidToken_ThrowsException() {
        when(repository.findByUnsubscribeToken("invalid-token")).thenReturn(Optional.empty());

        assertThrows(ApplicationException.class, () -> service.unsubscribe("invalid-token"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should correctly calculate stats dashboard metrics")
    void getStats_CalculatesMetricsCorrectly() {
        when(repository.count()).thenReturn(10L);
        when(repository.countByActive(true)).thenReturn(8L);
        when(repository.countByActive(false)).thenReturn(2L);
        when(repository.countActiveSubscribersNewerThan(any())).thenReturn(3L);

        List<Object[]> sourceCounts = new ArrayList<>();
        sourceCounts.add(new Object[]{"Footer", 5L});
        sourceCounts.add(new Object[]{"Pricing Page", 5L});
        when(repository.countSubscribersBySource()).thenReturn(sourceCounts);

        List<Object[]> monthlyCounts = new ArrayList<>();
        monthlyCounts.add(new Object[]{"2026-06", 10L});
        when(repository.countSubscribersByMonth()).thenReturn(monthlyCounts);

        NewsletterStatsDto stats = service.getStats();

        assertEquals(10, stats.getTotalSubscribers());
        assertEquals(8, stats.getActiveSubscribers());
        assertEquals(2, stats.getUnsubscribedSubscribers());
        assertEquals(3, stats.getNewThisMonth());
        assertEquals(5L, stats.getSubscribersBySource().get("Footer"));
        assertEquals(10L, stats.getSubscribersByMonth().get("2026-06"));
    }

    @Test
    @DisplayName("Should format exported CSV records properly")
    void exportCsv_FormatsCsvRecordsCorrectly() {
        NewsletterSubscriber s = new NewsletterSubscriber();
        s.setId(10L);
        s.setEmail("export@example.com");
        s.setActive(true);
        s.setSource("Blog");
        s.setSubscribedAt(LocalDateTime.of(2026, 6, 23, 12, 0, 0));

        when(repository.findAll()).thenReturn(Collections.singletonList(s));

        byte[] csvBytes = service.exportCsv(null, null, null);
        String csvContent = new String(csvBytes);

        assertTrue(csvContent.contains("ID,Email,Active,Subscribed At,Unsubscribed At,Source"));
        assertTrue(csvContent.contains("10,export@example.com,true,2026-06-23 12:00:00,,Blog"));
    }
}
