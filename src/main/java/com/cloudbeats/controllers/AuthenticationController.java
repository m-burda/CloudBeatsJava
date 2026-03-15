package com.cloudbeats.controllers;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.repositories.ApplicationUserRepository;
import com.cloudbeats.services.AuthenticationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AuthenticationService authenticationService;
    private final ApplicationUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public AuthenticationController(AuthenticationManager authenticationManager, AuthenticationService authenticationService, ApplicationUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.authenticationService = authenticationService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static class AuthenticationRequestBody {
        private String userName;
        private String password;
        private String email;

        // Getters and setters
        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username, request.password)
        );

        // 2. Set context for the current thread
        SecurityContext sc = SecurityContextHolder.getContext();
        sc.setAuthentication(auth);

        // 3. Persist the context into the session (This sends the Cookie back to the browser)
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", sc);

        return ResponseEntity.ok("Login successful");
    }

    private record LoginRequest(@NotNull String username, @NotNull String password) {}

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        if (userRepository.findByUsername(request.username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username exists");
        }

        ApplicationUser user = new ApplicationUser();
        user.setUsername(request.username);
        user.setPassword(passwordEncoder.encode(request.password));
        userRepository.save(user);

        return ResponseEntity.ok("User registered");
    }

    private record RegisterRequest(String username, String password) {}

    @PostMapping("/authenticate")
    public ResponseEntity<String> authenticate(@RequestBody AuthenticationRequestBody requestBody) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(requestBody.getUserName(), requestBody.getPassword())
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
}
