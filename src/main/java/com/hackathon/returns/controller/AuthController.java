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

@Controller
public class AuthController {
    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder enc;
    private final AuditService audit;

    public AuthController(AppUserRepository u, CustomerRepository c, PasswordEncoder e, AuditService a) {
        users = u; customers = c; enc = e; audit = a;
    }

    @GetMapping("/login")
    public String login(@RequestParam(defaultValue = "customer") String role, Model m) {
        m.addAttribute("role", role.toLowerCase());
        return "login";
    }

    @GetMapping("/go")
    public String go(Authentication a) {
        if (a == null) return "redirect:/login";
        String role = a.getAuthorities().iterator().next().getAuthority();
        return role.equals("ROLE_ADMIN") ? "redirect:/admin" : role.equals("ROLE_STAFF") ? "redirect:/dashboard" : "redirect:/portal";
    }

    @GetMapping("/register")
    public String register() { return "register"; }

    @PostMapping("/register")
    public String doRegister(@RequestParam String name, @RequestParam String email, @RequestParam String phone,
                             @RequestParam String password, @RequestParam String confirm, RedirectAttributes ra) {
        String mail = email.trim().toLowerCase();
        String err = null;
        if (name.isBlank() || phone.isBlank()) err = "Please fill in all fields.";
        else if (password.length() < 6) err = "Password must be at least 6 characters.";
        else if (!password.equals(confirm)) err = "Passwords do not match.";
        else if (users.findByEmailIgnoreCase(mail).isPresent()) err = "An account with this email already exists. Please log in.";
        if (err != null) { ra.addFlashAttribute("error", err); return "redirect:/register"; }

        users.save(new AppUser(name.trim(), mail, phone.trim(), enc.encode(password), "CUSTOMER"));
        if (customers.findByEmailIgnoreCase(mail).isEmpty()) customers.save(new Customer(name.trim(), mail, phone.trim()));
        audit.log(mail, "Registered a new customer account");
        ra.addFlashAttribute("success", "Account created successfully! Please log in.");
        return "redirect:/login?role=customer";
    }
}
