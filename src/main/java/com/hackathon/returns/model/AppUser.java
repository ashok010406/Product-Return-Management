package com.hackathon.returns.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Login account. role = ADMIN | STAFF | CUSTOMER */
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(unique = true) public String email;
    public String name, phone, password, role;
    public boolean active = true;
    public LocalDateTime createdAt = LocalDateTime.now();

    public AppUser() {}
    public AppUser(String name, String email, String phone, String password, String role) {
        this.name = name; this.email = email; this.phone = phone; this.password = password; this.role = role;
    }
}
