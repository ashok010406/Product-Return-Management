package com.hackathon.returns.controller;

import com.hackathon.returns.model.*;
import com.hackathon.returns.repository.*;
import com.hackathon.returns.service.AuditService;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Objects;

/** Administrator area: user management and audit trail. */
@Controller
@RequestMapping("/admin")
public class AdminController {
    private final AppUserRepository users;
    private final AuditLogRepository logs;
    private final PurchaseRepository purchases;
    private final ReturnRequestRepository returns;
    private final PasswordEncoder enc;
    private final AuditService audit;

    public AdminController(AppUserRepository u, AuditLogRepository l, PurchaseRepository p,
                           ReturnRequestRepository r, PasswordEncoder e, AuditService a) {
        users = u; logs = l; purchases = p; returns = r; enc = e; audit = a;
    }

    private List<AuditLog> recentLogs(int n) { return logs.findAll(Sort.by(Sort.Direction.DESC, "id")).stream().limit(n).toList(); }

    @GetMapping({"", "/"})
    public String home(Model m) {
        List<AppUser> all = users.findAll();
        m.addAttribute("admins", all.stream().filter(u -> u.role.equals("ADMIN")).count());
        m.addAttribute("staff", all.stream().filter(u -> u.role.equals("STAFF")).count());
        m.addAttribute("customerAccounts", all.stream().filter(u -> u.role.equals("CUSTOMER")).count());
        m.addAttribute("disabled", all.stream().filter(u -> !u.active).count());
        m.addAttribute("purchaseCount", purchases.count());
        m.addAttribute("returnCount", returns.count());
        m.addAttribute("pending", returns.findAll().stream().filter(r -> r.status.equals("PENDING")).count());
        m.addAttribute("logs", recentLogs(8));
        return "admin-home";
    }

    @GetMapping("/users")
    public String userList(Model m) { m.addAttribute("users", users.findAll()); return "admin-users"; }

    @PostMapping("/users")
    public String create(@RequestParam String name, @RequestParam String email, @RequestParam String phone,
                         @RequestParam String password, @RequestParam String role, RedirectAttributes ra) {
        String mail = email.trim().toLowerCase();
        if (!(role.equals("STAFF") || role.equals("ADMIN"))) ra.addFlashAttribute("error", "Invalid role.");
        else if (password.length() < 6) ra.addFlashAttribute("error", "Password must be at least 6 characters.");
        else if (users.findByEmailIgnoreCase(mail).isPresent()) ra.addFlashAttribute("error", "That email is already registered.");
        else {
            users.save(new AppUser(name.trim(), mail, phone.trim(), enc.encode(password), role));
            audit.log("Created " + role + " account for " + mail);
            ra.addFlashAttribute("success", role + " account created for " + mail);
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggle(@PathVariable Long id, Authentication a, RedirectAttributes ra) {
        AppUser u = users.findById(Objects.requireNonNull(id)).orElseThrow();
        if (u.email.equalsIgnoreCase(a.getName())) ra.addFlashAttribute("error", "You cannot deactivate your own account.");
        else {
            u.active = !u.active; users.save(u);
            audit.log((u.active ? "Activated " : "Deactivated ") + u.email);
            ra.addFlashAttribute("success", u.email + (u.active ? " activated." : " deactivated."));
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reset")
    public String reset(@PathVariable Long id, @RequestParam String password, RedirectAttributes ra) {
        if (password.length() < 6) { ra.addFlashAttribute("error", "Password must be at least 6 characters."); return "redirect:/admin/users"; }
        AppUser u = users.findById(Objects.requireNonNull(id)).orElseThrow();
        u.password = enc.encode(password); users.save(u);
        audit.log("Reset password for " + u.email);
        ra.addFlashAttribute("success", "Password reset for " + u.email);
        return "redirect:/admin/users";
    }

    @GetMapping("/audit")
    public String audit(Model m) { m.addAttribute("logs", recentLogs(150)); return "admin-audit"; }
}
