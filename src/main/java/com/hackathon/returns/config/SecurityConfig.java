package com.hackathon.returns.config;

import com.hackathon.returns.repository.AppUserRepository;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    /** Users come from the database (table app_user). */
    @Bean
    UserDetailsService userDetailsService(AppUserRepository repo) {
        return email -> repo.findByEmailIgnoreCase(email)
                .map(u -> User.withUsername(u.email).password(u.password).roles(u.role).disabled(!u.active).build())
                .orElseThrow(() -> new UsernameNotFoundException(email));
    }

    /** After login: send each role to its own area; block logging in on the wrong role tab. */
    @Bean
    AuthenticationSuccessHandler successHandler() {
        return (req, res, auth) -> {
            String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
            String chosen = req.getParameter("role");
            if (chosen != null && !chosen.isBlank() && !chosen.equalsIgnoreCase(role)) {
                new SecurityContextLogoutHandler().logout(req, res, auth);
                res.sendRedirect("/login?role=" + chosen + "&mismatch");
                return;
            }
            res.sendRedirect(switch (role) { case "ADMIN" -> "/admin"; case "STAFF" -> "/dashboard"; default -> "/portal"; });
        };
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, AuthenticationSuccessHandler success) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/", "/login", "/register", "/track", "/error", "/css/**").permitAll()
                .requestMatchers("/portal/**").hasRole("CUSTOMER")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**", "/dashboard", "/customers/**", "/purchases/**", "/returns/**").hasAnyRole("STAFF", "ADMIN")
                .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").successHandler(success)
                .failureHandler((req, res, ex) -> {
                    String r = req.getParameter("role");
                    res.sendRedirect("/login?error&role=" + (r == null ? "customer" : r));
                }).permitAll())
            .httpBasic(Customizer.withDefaults())
            .csrf(c -> c.ignoringRequestMatchers("/api/**"))
            .logout(l -> l.logoutSuccessUrl("/login?logout"));
        return http.build();
    }
}
