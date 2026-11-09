package com.hackathon.returns.model;

import jakarta.persistence.*;

@Entity
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    public String email;
    public String phone;

    public Customer() {}
    public Customer(String name, String email, String phone) { this.name = name; this.email = email; this.phone = phone; }
}
