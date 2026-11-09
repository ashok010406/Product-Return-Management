package com.hackathon.returns.service;

import com.hackathon.returns.model.AuditLog;
import com.hackathon.returns.repository.AuditLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repo;
    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    public void log(String action) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        log(a == null ? "system" : a.getName(), action);
    }
    public void log(String who, String action) {
        AuditLog l = new AuditLog(); l.who = who; l.action = action; repo.save(l);
    }
}
