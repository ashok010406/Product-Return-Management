package com.hackathon.returns;

import com.hackathon.returns.model.*;
import com.hackathon.returns.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** On first start (empty database) loads the sample dataset from src/main/resources/data/*.csv */
@Component
public class DataLoader implements CommandLineRunner {
    private final CustomerRepository customers;
    private final PurchaseRepository purchases;
    private final ReturnRequestRepository returns;
    private final AppUserRepository users;
    private final PasswordEncoder enc;

    public DataLoader(CustomerRepository c, PurchaseRepository p, ReturnRequestRepository r, AppUserRepository u, PasswordEncoder e) {
        customers = c; purchases = p; returns = r; users = u; enc = e;
    }

    @Override
    public void run(String... args) throws Exception {
        if (customers.count() == 0) loadDataset();
        if (users.count() == 0) createUsers();
    }

    private void createUsers() {
        String hash = enc.encode("customer123");
        users.save(new AppUser("System Admin", "admin@returnhub.com", "9000000001", enc.encode("admin123"), "ADMIN"));
        users.save(new AppUser("Support Staff", "staff@returnhub.com", "9000000002", enc.encode("staff123"), "STAFF"));
        for (Customer c : customers.findAll()) users.save(new AppUser(c.name, c.email, c.phone, hash, "CUSTOMER"));
        System.out.println("Login accounts created (admin, staff and " + customers.count() + " customers)");
    }

    private void loadDataset() throws Exception {
        Map<String, Customer> byEmail = new HashMap<>();
        for (String[] r : read("data/customers.csv"))
            byEmail.put(r[1], customers.save(new Customer(r[0], r[1], r[2])));

        List<Purchase> list = new ArrayList<>();
        for (String[] r : read("data/purchases.csv"))
            list.add(purchases.save(new Purchase(byEmail.get(r[0]), r[1], Double.parseDouble(r[2]),
                    Integer.parseInt(r[3]), LocalDate.now().minusDays(Long.parseLong(r[4])))));

        for (String[] r : read("data/returns.csv")) {
            ReturnRequest x = new ReturnRequest();
            x.purchase = list.get(Integer.parseInt(r[0]) - 1);
            x.reason = r[1];
            x.damageDescription = r[2].isBlank() ? null : r[2];
            x.status = r[3];
            x.staffRemarks = r[4].isBlank() ? null : r[4];
            x.createdAt = LocalDateTime.now().minusDays(Long.parseLong(r[5])).minusHours(Long.parseLong(r[6]));
            returns.save(x);
        }
        System.out.println("Sample dataset loaded: " + customers.count() + " customers, "
                + purchases.count() + " purchases, " + returns.count() + " returns");
    }

    private List<String[]> read(String path) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ClassPathResource(Objects.requireNonNull(path)).getInputStream(), StandardCharsets.UTF_8))) {
            return br.lines().skip(1).filter(l -> !l.isBlank()).map(l -> l.split(",", -1)).toList();
        }
    }
}
