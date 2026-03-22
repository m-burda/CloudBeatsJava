package com.cloudbeats.controllers;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.repositories.ApplicationUserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class AuthenticationController {
    private final AuthenticationManager authenticationManager;
    private final ApplicationUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public AuthenticationController(AuthenticationManager authenticationManager, ApplicationUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static class AuthenticationRequestBody {
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(
            @Valid
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email, request.password)
        );

        SecurityContext sc = SecurityContextHolder.getContext();
        sc.setAuthentication(auth);

        HttpSession session = servletRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", sc);

        return ResponseEntity.ok("Login successful");
    }

    private record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        String email,
        String password
    ) {}

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid
            @RequestBody RegisterRequest request
    ) {
        if (userRepository.existsByUsername(request.email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("User already exists");
        }

        ApplicationUser user = new ApplicationUser();
        user.setUsername(request.email);
        user.setPassword(passwordEncoder.encode(request.password));
        userRepository.save(user);

        return ResponseEntity.ok("User registered");
    }

    private record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Please provide a valid email address")
            String email,
//            @Size(min = 12, max = 20, message = "Password must be between 12 and 20 characters")
            String password
    ) {}

    @PostMapping("/authenticate")
    public ResponseEntity<String> authenticate(
            @Valid
            @RequestBody AuthenticationRequestBody requestBody
    ) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(requestBody.getEmail(), requestBody.getPassword())
            );

            User user = (User) authentication.getPrincipal();

            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", user.getUsername());

            String token = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(SignatureAlgorithm.HS256, jwtSecret)
                .compact();

            return ResponseEntity.ok(token);
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }

}
