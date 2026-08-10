package com.desitech.vyaparsathi.notification.dto;

import lombok.Data;
import java.util.Map;

@Data
public class NewsletterStatsDto {
    private long totalSubscribers;
    private long activeSubscribers;
    private long unsubscribedSubscribers;
    private long newThisMonth;
    private Map<String, Long> subscribersBySource;
    private Map<String, Long> subscribersByMonth;

    public long getTotalSubscribers() { return totalSubscribers; }
    public void setTotalSubscribers(long totalSubscribers) { this.totalSubscribers = totalSubscribers; }

    public long getActiveSubscribers() { return activeSubscribers; }
    public void setActiveSubscribers(long activeSubscribers) { this.activeSubscribers = activeSubscribers; }

    public long getUnsubscribedSubscribers() { return unsubscribedSubscribers; }
    public void setUnsubscribedSubscribers(long unsubscribedSubscribers) { this.unsubscribedSubscribers = unsubscribedSubscribers; }

    public long getNewThisMonth() { return newThisMonth; }
    public void setNewThisMonth(long newThisMonth) { this.newThisMonth = newThisMonth; }

    public Map<String, Long> getSubscribersBySource() { return subscribersBySource; }
    public void setSubscribersBySource(Map<String, Long> subscribersBySource) { this.subscribersBySource = subscribersBySource; }

    public Map<String, Long> getSubscribersByMonth() { return subscribersByMonth; }
    public void setSubscribersByMonth(Map<String, Long> subscribersByMonth) { this.subscribersByMonth = subscribersByMonth; }
}
