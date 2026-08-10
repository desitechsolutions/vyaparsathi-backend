package com.desitech.vyaparsathi.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NotificationEvent extends ApplicationEvent {

    private final String type;
    private final String title;
    private final String message;
    private final String recipient;
    private final String link;
    private final String priority;

    public NotificationEvent(Object source,
                             String type,
                             String title,
                             String message,
                             String recipient,
                             String link,
                             String priority) {
        super(source);
        this.type = type;
        this.title = title;
        this.message = message;
        this.recipient = recipient;
        this.link = link;
        this.priority = priority != null ? priority : "medium";
    }

    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getRecipient() { return recipient; }
    public String getLink() { return link; }
    public String getPriority() { return priority; }
}