package com.hackathon.returns;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import java.awt.Desktop;
import java.net.URI;

@SpringBootApplication
public class ReturnManagementApplication {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(ReturnManagementApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        String port = System.getenv("PORT") != null ? System.getenv("PORT") : "8080";
        String url = "http://localhost:" + port;
        System.out.println("\n=====================================================");
        System.out.println("  ReturnHub is running!  Open:  " + url);
        System.out.println("  Customer: aarav@mail.com / customer123");
        System.out.println("  Staff:    staff@returnhub.com / staff123");
        System.out.println("  Admin:    admin@returnhub.com / admin123");
        System.out.println("=====================================================\n");
        if (System.getenv("PORT") != null) return; // live host: no local browser
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) { }
        // Fallback when Desktop.browse is unavailable (e.g. started from an IDE/terminal)
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            else if (os.contains("mac")) new ProcessBuilder("open", url).start();
            else new ProcessBuilder("xdg-open", url).start();
        } catch (Exception e) {
            System.out.println("Could not open the browser automatically. Please open " + url + " manually.");
        }
    }
}
