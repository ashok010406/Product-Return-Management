package com.hackathon.returns.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Purchase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne public Customer customer;
    public String productName;
    public double price;
    public int quantity;
    public LocalDate purchaseDate;

    public Purchase() {}
    public Purchase(Customer c, String p, double price, int q, LocalDate d) {
        this.customer = c; this.productName = p; this.price = price; this.quantity = q; this.purchaseDate = d;
    }
}
