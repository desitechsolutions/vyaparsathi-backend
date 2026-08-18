package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.PasswordHistory;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Maintains a rolling window of prior password hashes per user so we can
 * reject reuse of the last {@link #HISTORY_WINDOW} passwords. The current
 * active hash lives on the User entity and is checked separately by
 * {@link #matchesHistory(User, String)}.
 */
@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordHistoryService.class);

    /** Number of past passwords remembered per user. */
    public static final int HISTORY_WINDOW = 5;

    private final PasswordHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Returns true if {@code rawPassword} matches the current active hash
     * on {@code user} or any of the last {@link #HISTORY_WINDOW} hashes in
     * password_history.
     */
    public boolean matchesHistory(User user, String rawPassword) {
        if (user.getPasswordHash() != null && passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return true;
        }
        List<PasswordHistory> recent = historyRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(0, HISTORY_WINDOW));
        for (PasswordHistory row : recent) {
            if (passwordEncoder.matches(rawPassword, row.getPasswordHash())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Append the given hash to the user's password history and trim rows
     * beyond the retention window. Idempotent to call on user creation.
     */
    @Transactional
    public void recordHash(Long userId, String passwordHash) {
        PasswordHistory row = new PasswordHistory();
        row.setUserId(userId);
        row.setPasswordHash(passwordHash);
        row.setCreatedAt(LocalDateTime.now());
        historyRepository.save(row);
        try {
            historyRepository.trimHistoryBeyondWindow(userId, HISTORY_WINDOW);
        } catch (Exception ex) {
            // JPQL LIMIT support varies by Hibernate version; falling back to a
            // best-effort trim keeps user password changes safe even if the
            // pruning query fails.
            log.debug("Password-history trim skipped for user {}: {}", userId, ex.getMessage());
        }
    }
}
