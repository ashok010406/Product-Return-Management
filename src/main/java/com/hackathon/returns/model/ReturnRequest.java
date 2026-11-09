package com.hackathon.returns.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ReturnRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne public Purchase purchase;
    public String reason;
    @Column(length = 1000) public String damageDescription;
    public String status = "PENDING";   // PENDING / APPROVED / REJECTED
    public String staffRemarks;
    public LocalDateTime createdAt = LocalDateTime.now();
}
