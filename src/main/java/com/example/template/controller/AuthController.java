package com.example.template.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.template.config.AdminOnly;
import com.example.template.config.UserOnly;
import com.example.template.model.LoginReq;
import com.example.template.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginReq req) {
        // Bad credentials throw BadCredentialsException, handled centrally as 401
        // by GlobalExceptionHandler; invalid request bodies become 400 there too.
        String token = authService.login(req.username(), req.password());
        return ResponseEntity.ok(Map.of("token", token));
    }

    // @PreAuthorize (via @AdminOnly) rejects unauthorized callers before this runs,
    // so Authentication is always present and already carries the right roles.
    @AdminOnly
    @GetMapping("/admin-roles")
    public ResponseEntity<Map<String, Object>> getAdminRoles(Authentication authentication) {
        return ResponseEntity.ok(principalInfo(authentication));
    }

    @UserOnly
    @GetMapping("/user-roles")
    public ResponseEntity<Map<String, Object>> getUserRoles(Authentication authentication) {
        return ResponseEntity.ok(principalInfo(authentication));
    }

    private Map<String, Object> principalInfo(Authentication authentication) {
        return Map.of(
                "username", authentication.getName(),
                "roles", authentication.getAuthorities().stream().map(Object::toString).toList());
    }
}
