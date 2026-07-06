package com.example.template.service;

import java.util.Optional;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.template.config.JwtUtil;
import com.example.template.model.entity.User;
import com.example.template.repository.UserRepository;

import io.micrometer.observation.annotation.Observed;

@Service
public class AuthService {

    // A valid BCrypt hash used only to keep the "unknown user" path as slow as the
    // "wrong password" path, so response timing cannot be used to enumerate usernames.
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAuditService loginAuditService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, LoginAuditService loginAuditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.loginAuditService = loginAuditService;
    }

    @Observed(name = "auth.login", contextualName = "user-login")
    public String login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            // Hash anyway so a missing user costs the same time as a wrong password.
            passwordEncoder.matches(password, DUMMY_HASH);
            throw new BadCredentialsException("Invalid username or password");
        }
        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        loginAuditService.recordLogin(username);
        return jwtUtil.generateToken(user.getUsername(), user.getRoles());
    }
}
