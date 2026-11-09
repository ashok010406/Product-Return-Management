package com.hackathon.returns.controller;

import com.hackathon.returns.model.*;
import com.hackathon.returns.repository.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class WebController {
    private final CustomerRepository customers;
    private final PurchaseRepository purchases;
    private final ReturnRequestRepository returns;
    private final com.hackathon.returns.service.AuditService audit;

    public WebController(CustomerRepository c, PurchaseRepository p, ReturnRequestRepository r, com.hackathon.returns.service.AuditService audit) {
        this.customers = c; this.purchases = p; this.returns = r; this.audit = audit;
    }

    // ================= PUBLIC =================
    @GetMapping("/")
    public String home() { return "home"; }

    @GetMapping("/track")
    public String track(@RequestParam(required = false) Long id, @RequestParam(required = false) String email, Model m) {
        if (id != null && email != null) {
            ReturnRequest r = returns.findById(id).orElse(null);
            if (r != null && r.purchase.customer.email.equalsIgnoreCase(email.trim())) m.addAttribute("result", r);
            else m.addAttribute("error", "No return found for that ID and email.");
        }
        return "track";
    }

    // ================= STAFF =================
    public static class Stat {
        public String label; public long count, pct;
        public Stat(String l, long c, long p) { label = l; count = c; pct = p; }
    }

    private List<Stat> top(Map<String, Long> map, int n) {
        long max = map.values().stream().mapToLong(v -> Objects.requireNonNullElse(v, 0L)).max().orElse(1);
        return map.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(n)
                .map(e -> new Stat(e.getKey(), e.getValue(), Math.round(e.getValue() * 100.0 / max))).toList();
    }

    @GetMapping("/dashboard")
    public String dashboard(Model m) {
        List<ReturnRequest> all = returns.findAll();
        List<Purchase> allP = purchases.findAll();
        long total = all.size();
        long pending = all.stream().filter(r -> r.status.equals("PENDING")).count();
        long approved = all.stream().filter(r -> r.status.equals("APPROVED")).count();
        long rejected = all.stream().filter(r -> r.status.equals("REJECTED")).count();
        double refund = all.stream().filter(r -> r.status.equals("APPROVED")).mapToDouble(r -> r.purchase.price * r.purchase.quantity).sum();
        double sales = allP.stream().mapToDouble(p -> p.price * p.quantity).sum();
        long decided = approved + rejected;
        m.addAttribute("today", LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        m.addAttribute("customerCount", customers.count());
        m.addAttribute("purchaseCount", allP.size());
        m.addAttribute("total", total);
        m.addAttribute("pending", pending);
        m.addAttribute("approved", approved);
        m.addAttribute("rejected", rejected);
        m.addAttribute("rate", decided == 0 ? 0 : Math.round(approved * 100.0 / decided));
        m.addAttribute("returnRate", allP.isEmpty() ? 0 : Math.round(total * 100.0 / allP.size()));
        m.addAttribute("refund", refund);
        m.addAttribute("sales", sales);
        m.addAttribute("damaged", all.stream().filter(r -> r.damageDescription != null).count());
        m.addAttribute("damagedPending", all.stream().filter(r -> r.status.equals("PENDING") && r.damageDescription != null).count());
        String donut = "#e5e7eb 0 100%";
        long dn = pending + approved + rejected;
        if (dn > 0) {
            double x = pending * 100.0 / dn, y = x + approved * 100.0 / dn;
            donut = "#f59e0b 0 " + x + "%, #10b981 " + x + "% " + y + "%, #f43f5e " + y + "% 100%";
        }
        m.addAttribute("donut", "conic-gradient(" + donut + ")");
        // last 7 days trend
        long[] counts = new long[7];
        for (int i = 0; i < 7; i++) {
            LocalDate d = LocalDate.now().minusDays(6 - i);
            counts[i] = all.stream().filter(r -> r.createdAt.toLocalDate().equals(d)).count();
        }
        long mx = Math.max(1, Arrays.stream(counts).max().orElse(1));
        List<Stat> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++)
            trend.add(new Stat(LocalDate.now().minusDays(6 - i).getDayOfWeek().toString().substring(0, 3),
                    counts[i], Math.max(4, Math.round(counts[i] * 100.0 / mx))));
        m.addAttribute("trend", trend);
        m.addAttribute("reasons", top(all.stream().collect(Collectors.groupingBy(r -> r.reason, Collectors.counting())), 5));
        m.addAttribute("topProducts", top(all.stream().collect(Collectors.groupingBy(r -> r.purchase.productName, Collectors.counting())), 5));
        m.addAttribute("topCustomers", top(all.stream().collect(Collectors.groupingBy(r -> r.purchase.customer.name, Collectors.counting())), 5));
        m.addAttribute("queue", all.stream().filter(r -> r.status.equals("PENDING"))
                .sorted(Comparator.comparing((ReturnRequest r) -> r.createdAt)).limit(5).toList());
        m.addAttribute("recent", all.stream().sorted(Comparator.comparing((ReturnRequest r) -> r.createdAt).reversed()).limit(6).toList());
        return "dashboard";
    }

    // ----- customers (search, add, edit, delete) -----
    @GetMapping("/customers")
    public String customerList(@RequestParam(required = false) String q, Model m) {
        String k = q == null ? "" : q.trim().toLowerCase();
        m.addAttribute("customers", customers.findAll().stream()
                .filter(c -> k.isEmpty() || c.name.toLowerCase().contains(k) || c.email.toLowerCase().contains(k)).toList());
        m.addAttribute("q", q);
        return "customers";
    }

    @PostMapping("/customers")
    public String addCustomer(@RequestParam String name, @RequestParam String email, @RequestParam String phone) {
        customers.save(new Customer(name.trim(), email.trim().toLowerCase(), phone.trim()));
        return "redirect:/customers";
    }

    @GetMapping("/customers/{id}/edit")
    public String editCustomer(@PathVariable Long id, Model m) {
        m.addAttribute("c", customers.findById(Objects.requireNonNull(id)).orElseThrow());
        return "customer-edit";
    }

    @PostMapping("/customers/{id}")
    public String updateCustomer(@PathVariable Long id, @RequestParam String name, @RequestParam String email, @RequestParam String phone) {
        Customer c = customers.findById(Objects.requireNonNull(id)).orElseThrow();
        c.name = name.trim(); c.email = email.trim().toLowerCase(); c.phone = phone.trim();
        customers.save(c);
        return "redirect:/customers";
    }

    @PostMapping("/customers/{id}/delete")
    public String deleteCustomer(@PathVariable Long id, RedirectAttributes ra) {
        if (purchases.findAll().stream().anyMatch(p -> p.customer.id.equals(id)))
            ra.addFlashAttribute("error", "Cannot delete a customer who has purchases.");
        else customers.deleteById(Objects.requireNonNull(id));
        return "redirect:/customers";
    }

    // ----- purchases -----
    @GetMapping("/purchases")
    public String purchaseList(@RequestParam(required = false) String q, Model m) {
        String k = q == null ? "" : q.trim().toLowerCase();
        m.addAttribute("purchases", purchases.findAll().stream()
                .filter(p -> k.isEmpty() || p.productName.toLowerCase().contains(k) || p.customer.name.toLowerCase().contains(k)).toList());
        m.addAttribute("customers", customers.findAll());
        m.addAttribute("q", q);
        return "purchases";
    }

    @PostMapping("/purchases")
    public String addPurchase(@RequestParam Long customerId, @RequestParam String productName,
                              @RequestParam double price, @RequestParam int quantity,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate) {
        purchases.save(new Purchase(customers.findById(Objects.requireNonNull(customerId)).orElseThrow(), productName, price, quantity, purchaseDate));
        return "redirect:/purchases";
    }

    @PostMapping("/purchases/{id}/delete")
    public String deletePurchase(@PathVariable Long id, RedirectAttributes ra) {
        if (returns.existsByPurchase_Id(id)) ra.addFlashAttribute("error", "Cannot delete a purchase that has a return request.");
        else purchases.deleteById(Objects.requireNonNull(id));
        return "redirect:/purchases";
    }

    // ----- return requests (filter, search, decide, export) -----
    private List<ReturnRequest> filtered(String status, String q) {
        String k = q == null ? "" : q.trim().toLowerCase();
        return returns.findAll().stream()
                .filter(r -> status == null || status.isEmpty() || r.status.equals(status))
                .filter(r -> k.isEmpty() || r.purchase.productName.toLowerCase().contains(k) || r.purchase.customer.name.toLowerCase().contains(k))
                .sorted(Comparator.comparing((ReturnRequest r) -> r.createdAt).reversed()).toList();
    }

    @GetMapping("/returns")
    public String returnList(@RequestParam(required = false) String status, @RequestParam(required = false) String q, Model m) {
        m.addAttribute("returns", filtered(status, q));
        m.addAttribute("status", status == null ? "" : status);
        m.addAttribute("q", q);
        return "returns";
    }

    @GetMapping("/returns/export")
    @ResponseBody
    public ResponseEntity<String> export(@RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        StringBuilder sb = new StringBuilder("ID,Customer,Product,Amount,Reason,Damage details,Status,Remarks,Submitted\n");
        for (ReturnRequest r : filtered(status, q))
            sb.append(r.id).append(',').append(esc(r.purchase.customer.name)).append(',').append(esc(r.purchase.productName)).append(',')
              .append(r.purchase.price * r.purchase.quantity).append(',').append(esc(r.reason)).append(',').append(esc(r.damageDescription)).append(',')
              .append(r.status).append(',').append(esc(r.staffRemarks)).append(',').append(r.createdAt).append('\n');
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=returns.csv")
                .contentType(MediaType.parseMediaType("text/csv")).body(sb.toString());
    }

    private String esc(String s) { return s == null ? "" : "\"" + s.replace("\"", "\"\"") + "\""; }

    @PostMapping("/returns/{id}/decision")
    public String decide(@PathVariable Long id, @RequestParam String action, @RequestParam(required = false) String remarks,
                         @RequestParam(required = false) String from) {
        ReturnRequest r = returns.findById(Objects.requireNonNull(id)).orElseThrow();
        if (r.status.equals("PENDING")) {
            r.status = action.equals("approve") ? "APPROVED" : "REJECTED";
            r.staffRemarks = remarks;
            returns.save(r);
            audit.log("Return #" + id + " " + r.status + " (" + r.purchase.productName + ")");
        }
        return "dashboard".equals(from) ? "redirect:/dashboard" : "redirect:/returns?status=PENDING";
    }
}
