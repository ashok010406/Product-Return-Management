package com.hackathon.returns.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String who;
    @Column(length = 500) public String action;
    public LocalDateTime createdAt = LocalDateTime.now();
}
