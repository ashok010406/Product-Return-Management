package com.hackathon.returns.controller;

import com.hackathon.returns.model.*;
import com.hackathon.returns.repository.*;
import com.hackathon.returns.service.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Everything a logged-in CUSTOMER can do. */
@Controller
@RequestMapping("/portal")
public class PortalController {
    private static final int WINDOW = 30;

    public static class Product {
        public String name, icon, desc; public double price;
        Product(String n, String i, double p, String d) { name = n; icon = i; price = p; desc = d; }
    }
    public static class OrderView { public Purchase p; public boolean eligible; public String status; public long daysLeft; }

    private static final List<Product> CATALOG = List.of(
            new Product("Wireless Headphones", "&#127911;", 2499, "40-hour battery, deep bass"),
            new Product("Smart Watch", "&#8986;", 5999, "Heart-rate, GPS, 7-day battery"),
            new Product("Bluetooth Speaker", "&#128264;", 1799, "Waterproof, 360 degree sound"),
            new Product("Gaming Mouse", "&#128433;", 1299, "16000 DPI, RGB lighting"),
            new Product("Mechanical Keyboard", "&#9000;", 3499, "Hot-swap switches, backlit"),
            new Product("Power Bank 20000mAh", "&#128267;", 1899, "Fast charge, dual output"),
            new Product("Webcam Full HD", "&#128247;", 2199, "1080p with noise-cancelling mic"),
            new Product("Portable SSD 1TB", "&#128190;", 6499, "1000 MB/s transfer speed"));

    private final CustomerRepository customers;
    private final PurchaseRepository purchases;
    private final ReturnRequestRepository returns;
    private final AppUserRepository users;
    private final PasswordEncoder enc;
    private final AuditService audit;

    public PortalController(CustomerRepository c, PurchaseRepository p, ReturnRequestRepository r,
                            AppUserRepository u, PasswordEncoder e, AuditService a) {
        customers = c; purchases = p; returns = r; users = u; enc = e; audit = a;
    }

    private Customer me(Authentication a) { return customers.findByEmailIgnoreCase(a.getName()).orElseThrow(); }

    private List<Purchase> myPurchases(Authentication a) {
        return purchases.findAll().stream().filter(x -> x.customer.email.equalsIgnoreCase(a.getName()))
                .sorted(Comparator.comparing((Purchase x) -> x.purchaseDate).reversed()).toList();
    }

    private List<ReturnRequest> myReturns(Authentication a) {
        return returns.findAll().stream().filter(x -> x.purchase.customer.email.equalsIgnoreCase(a.getName()))
                .sorted(Comparator.comparing((ReturnRequest x) -> x.createdAt).reversed()).toList();
    }

    // ---------- dashboard ----------
    @GetMapping({"", "/"})
    public String home(Authentication a, Model m) {
        List<ReturnRequest> mine = myReturns(a);
        m.addAttribute("name", users.findByEmailIgnoreCase(a.getName()).orElseThrow().name);
        m.addAttribute("orders", myPurchases(a).size());
        m.addAttribute("total", mine.size());
        m.addAttribute("pending", mine.stream().filter(x -> x.status.equals("PENDING")).count());
        m.addAttribute("approved", mine.stream().filter(x -> x.status.equals("APPROVED")).count());
        m.addAttribute("rejected", mine.stream().filter(x -> x.status.equals("REJECTED")).count());
        m.addAttribute("refund", mine.stream().filter(x -> x.status.equals("APPROVED"))
                .mapToDouble(x -> x.purchase.price * x.purchase.quantity).sum());
        m.addAttribute("recent", mine.stream().limit(4).toList());
        return "portal-dashboard";
    }

    // ---------- shop ----------
    @GetMapping("/shop")
    public String shop(Model m) { m.addAttribute("products", CATALOG); return "portal-shop"; }

    @PostMapping("/buy")
    public String buy(@RequestParam int product, @RequestParam(defaultValue = "1") int quantity,
                      Authentication a, RedirectAttributes ra) {
        if (product < 0 || product >= CATALOG.size() || quantity < 1 || quantity > 10) {
            ra.addFlashAttribute("error", "Invalid product or quantity."); return "redirect:/portal/shop";
        }
        Product pr = CATALOG.get(product);
        purchases.save(new Purchase(me(a), pr.name, pr.price, quantity, LocalDate.now()));
        audit.log("Purchased " + quantity + " x " + pr.name);
        ra.addFlashAttribute("success", "Order placed: " + quantity + " x " + pr.name + ". You can return it within " + WINDOW + " days.");
        return "redirect:/portal/orders";
    }

    // ---------- orders ----------
    @GetMapping("/orders")
    public String orders(Authentication a, Model m) {
        List<ReturnRequest> mine = myReturns(a);
        List<OrderView> views = new ArrayList<>();
        for (Purchase pu : myPurchases(a)) {
            OrderView v = new OrderView(); v.p = pu;
            ReturnRequest active = mine.stream().filter(x -> x.purchase.id.equals(pu.id) && !x.status.equals("CANCELLED")).findFirst().orElse(null);
            v.status = active == null ? null : active.status;
            v.daysLeft = WINDOW - ChronoUnit.DAYS.between(pu.purchaseDate, LocalDate.now());
            v.eligible = active == null && v.daysLeft >= 0;
            views.add(v);
        }
        m.addAttribute("orders", views);
        return "portal-orders";
    }

    // ---------- returns ----------
    @GetMapping("/returns")
    public String returnList(Authentication a, Model m) { m.addAttribute("returns", myReturns(a)); return "portal-returns"; }

    @GetMapping("/returns/new")
    public String returnForm(@RequestParam Long orderId, Authentication a, Model m, RedirectAttributes ra) {
        Purchase p = purchases.findById(Objects.requireNonNull(orderId)).orElse(null);
        if (p == null || !p.customer.email.equalsIgnoreCase(a.getName())) {
            ra.addFlashAttribute("error", "Order not found."); return "redirect:/portal/orders";
        }
        m.addAttribute("p", p);
        m.addAttribute("daysLeft", WINDOW - ChronoUnit.DAYS.between(p.purchaseDate, LocalDate.now()));
        return "portal-return-form";
    }

    @PostMapping("/returns")
    public String submit(@RequestParam Long orderId, @RequestParam String reason,
                         @RequestParam(required = false) String damageDescription, Authentication a, RedirectAttributes ra) {
        Purchase p = purchases.findById(Objects.requireNonNull(orderId)).orElse(null);
        String back = "redirect:/portal/returns/new?orderId=" + orderId;
        if (p == null || !p.customer.email.equalsIgnoreCase(a.getName())) {
            ra.addFlashAttribute("error", "Order not found."); return "redirect:/portal/orders";
        }
        if (ChronoUnit.DAYS.between(p.purchaseDate, LocalDate.now()) > WINDOW) {
            ra.addFlashAttribute("error", "Returns are accepted only within " + WINDOW + " days of purchase."); return "redirect:/portal/orders";
        }
        if (myReturns(a).stream().anyMatch(x -> x.purchase.id.equals(orderId) && !x.status.equals("CANCELLED"))) {
            ra.addFlashAttribute("error", "A return request already exists for this order."); return "redirect:/portal/returns";
        }
        // BUSINESS RULE: damaged product => detailed explanation required
        if (reason.equals("Damaged Product") && (damageDescription == null || damageDescription.trim().length() < 20)) {
            ra.addFlashAttribute("error", "Damaged product: please give a detailed explanation (at least 20 characters).");
            return back;
        }
        ReturnRequest r = new ReturnRequest();
        r.purchase = p; r.reason = reason;
        r.damageDescription = reason.equals("Damaged Product") ? damageDescription.trim() : null;
        returns.save(r);
        audit.log("Submitted return #" + r.id + " for " + p.productName + " (" + reason + ")");
        ra.addFlashAttribute("success", "Return request #" + r.id + " submitted. Our team will review it shortly.");
        return "redirect:/portal/returns";
    }

    @PostMapping("/returns/{id}/cancel")
    public String cancel(@PathVariable Long id, Authentication a, RedirectAttributes ra) {
        ReturnRequest r = returns.findById(Objects.requireNonNull(id)).orElse(null);
        if (r == null || !r.purchase.customer.email.equalsIgnoreCase(a.getName()) || !r.status.equals("PENDING")) {
            ra.addFlashAttribute("error", "Only your own pending requests can be cancelled.");
        } else {
            r.status = "CANCELLED"; r.staffRemarks = "Cancelled by customer"; returns.save(r);
            audit.log("Cancelled return #" + id);
            ra.addFlashAttribute("success", "Return request #" + id + " cancelled.");
        }
        return "redirect:/portal/returns";
    }

    // ---------- profile ----------
    @GetMapping("/profile")
    public String profile(Authentication a, Model m) {
        m.addAttribute("u", users.findByEmailIgnoreCase(a.getName()).orElseThrow());
        return "portal-profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String name, @RequestParam String phone, Authentication a, RedirectAttributes ra) {
        AppUser u = users.findByEmailIgnoreCase(a.getName()).orElseThrow();
        u.name = name.trim(); u.phone = phone.trim(); users.save(u);
        Customer c = me(a); c.name = u.name; c.phone = u.phone; customers.save(c);
        audit.log("Updated profile");
        ra.addFlashAttribute("success", "Profile updated.");
        return "redirect:/portal/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@RequestParam String current, @RequestParam String password, Authentication a, RedirectAttributes ra) {
        AppUser u = users.findByEmailIgnoreCase(a.getName()).orElseThrow();
        if (!enc.matches(current, u.password)) ra.addFlashAttribute("error", "Current password is incorrect.");
        else if (password.length() < 6) ra.addFlashAttribute("error", "New password must be at least 6 characters.");
        else { u.password = enc.encode(password); users.save(u); audit.log("Changed password"); ra.addFlashAttribute("success", "Password changed."); }
        return "redirect:/portal/profile";
    }
}
