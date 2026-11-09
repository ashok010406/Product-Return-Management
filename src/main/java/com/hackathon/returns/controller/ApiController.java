package com.hackathon.returns.controller;

import com.hackathon.returns.model.*;
import com.hackathon.returns.repository.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** REST backend (JSON). Staff only: use HTTP Basic auth (admin / admin123) in Postman or curl. */
@RestController
@RequestMapping("/api")
public class ApiController {
    private final CustomerRepository customers;
    private final PurchaseRepository purchases;
    private final ReturnRequestRepository returns;

    public ApiController(CustomerRepository c, PurchaseRepository p, ReturnRequestRepository r) {
        customers = c; purchases = p; returns = r;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        var all = returns.findAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customers", customers.count());
        m.put("purchases", purchases.count());
        m.put("returns", all.size());
        for (String s : List.of("PENDING", "APPROVED", "REJECTED"))
            m.put(s.toLowerCase(), all.stream().filter(r -> r.status.equals(s)).count());
        return m;
    }

    @GetMapping("/customers")  public List<Customer> customers() { return customers.findAll(); }
    @GetMapping("/purchases")  public List<Purchase> purchases() { return purchases.findAll(); }
    @GetMapping("/returns")    public List<ReturnRequest> returns() { return returns.findAll(); }

    @GetMapping("/returns/{id}")
    public ResponseEntity<ReturnRequest> one(@PathVariable Long id) {
        return returns.findById(Objects.requireNonNull(id)).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/customers")
    public Customer addCustomer(@RequestBody Customer c) { c.id = null; return customers.save(c); }

    /** Body: {"customerId":1,"productName":"Mouse","price":999,"quantity":1,"purchaseDate":"2026-11-01"} */
    @PostMapping("/purchases")
    public ResponseEntity<?> addPurchase(@RequestBody Map<String, Object> b) {
        Customer c = customers.findById(Objects.requireNonNull(Long.valueOf(b.get("customerId").toString()))).orElse(null);
        if (c == null) return ResponseEntity.badRequest().body(Map.of("error", "Unknown customerId"));
        return ResponseEntity.ok(purchases.save(new Purchase(c, b.get("productName").toString(),
                Double.parseDouble(b.get("price").toString()), Integer.parseInt(b.get("quantity").toString()),
                java.time.LocalDate.parse(b.get("purchaseDate").toString()))));
    }

    /** Body: {"purchaseId":1,"reason":"Damaged Product","damageDescription":"..."} */
    @PostMapping("/returns")
    public ResponseEntity<?> addReturn(@RequestBody Map<String, String> b) {
        Purchase p = purchases.findById(Objects.requireNonNull(Long.valueOf(b.get("purchaseId")))).orElse(null);
        if (p == null) return ResponseEntity.badRequest().body(Map.of("error", "Unknown purchaseId"));
        String reason = b.get("reason"), dmg = b.get("damageDescription");
        if ("Damaged Product".equals(reason) && (dmg == null || dmg.trim().length() < 20))
            return ResponseEntity.badRequest().body(Map.of("error", "Damaged product requires a detailed explanation (min 20 characters)"));
        ReturnRequest r = new ReturnRequest();
        r.purchase = p; r.reason = reason; r.damageDescription = "Damaged Product".equals(reason) ? dmg.trim() : null;
        return ResponseEntity.ok(returns.save(r));
    }

    /** Body: {"status":"APPROVED","remarks":"ok"} */
    @PutMapping("/returns/{id}/decision")
    public ResponseEntity<?> decide(@PathVariable Long id, @RequestBody Map<String, String> b) {
        ReturnRequest r = returns.findById(Objects.requireNonNull(id)).orElse(null);
        if (r == null) return ResponseEntity.notFound().build();
        String s = String.valueOf(b.get("status")).toUpperCase();
        if (!s.equals("APPROVED") && !s.equals("REJECTED")) return ResponseEntity.badRequest().body(Map.of("error", "status must be APPROVED or REJECTED"));
        r.status = s; r.staffRemarks = b.get("remarks");
        return ResponseEntity.ok(returns.save(r));
    }
}
