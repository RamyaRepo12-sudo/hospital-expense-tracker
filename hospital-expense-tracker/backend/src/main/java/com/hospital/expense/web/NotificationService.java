package com.hospital.expense.web;

import com.hospital.expense.model.Expense;
import com.hospital.expense.model.RecurringRule;
import com.hospital.expense.repo.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Sends plain-text emails when MAIL_ENABLED=true and an SMTP server is configured. Never blocks or breaks a request. */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Set<String> APPROVER_ROLES = Set.of("SUPER_ADMIN", "FINANCE_ADMIN", "DEPARTMENT_MANAGER");

    private final ObjectProvider<JavaMailSender> mail;
    private final UserRepository users;
    private final boolean enabled;
    private final String from, appUrl;

    public NotificationService(ObjectProvider<JavaMailSender> mail, UserRepository users, @Value("${app.mail.enabled}") boolean enabled,
                               @Value("${app.mail.from}") String from, @Value("${app.public-url}") String appUrl) {
        this.mail = mail; this.users = users; this.enabled = enabled; this.from = from; this.appUrl = appUrl;
    }

    @Async
    public void submitted(Expense e) {
        List<String> to = users.findAll().stream()
                .filter(u -> u.active && APPROVER_ROLES.contains(u.role) && u.email != null && !u.email.isBlank() && !u.username.equals(e.createdBy))
                .map(u -> u.email).toList();
        send(to, "Approval needed: " + e.expenseNo, describe(e) + "\n\nReview it here: " + appUrl + "/approvals");
    }

    @Async
    public void decided(Expense e, String decision, String actor) {
        users.findByUsername(e.createdBy).filter(u -> u.active && u.email != null && !u.email.isBlank()).ifPresent(u -> {
            String extra = "Rejected".equals(decision) && e.rejectionReason != null ? "\nReason: " + e.rejectionReason : "";
            send(List.of(u.email), "Expense " + e.expenseNo + " " + decision.toLowerCase(),
                    describe(e) + "\n\nStatus: " + decision + " by " + actor + extra + "\n\nOpen the app: " + appUrl + "/expenses");
        });
    }

    @Async
    public void recurringReady(String username, int count, RecurringRule rule) {
        users.findByUsername(username).filter(u -> u.active && u.email != null && !u.email.isBlank()).ifPresent(u ->
                send(List.of(u.email), count + " recurring expense draft" + (count > 1 ? "s" : "") + " ready",
                        "A recurring rule created " + count + " draft" + (count > 1 ? "s" : "") + " for " + rule.vendor + " (" + rule.department + ").\nPlease check the invoice details and submit " + (count > 1 ? "them" : "it") + ".\n\nOpen the app: " + appUrl + "/expenses"));
    }

    private static String describe(Expense e) {
        return e.expenseNo + " · " + e.vendor + "\nDepartment: " + e.department + "\nCategory: " + e.category
                + "\nTotal: INR " + e.getTotal().toPlainString() + "\nSubmitted by: " + e.createdBy;
    }

    private void send(Collection<String> recipients, String subject, String text) {
        if (!enabled || recipients.isEmpty()) return;
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) { log.warn("MAIL_ENABLED=true but no SMTP server is configured (set SPRING_MAIL_HOST)."); return; }
        for (String to : recipients) {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(from); m.setTo(to); m.setSubject(subject); m.setText(text);
                sender.send(m);
            } catch (Exception ex) {
                log.warn("Email to {} failed: {}", to, ex.getMessage());
            }
        }
    }
}
