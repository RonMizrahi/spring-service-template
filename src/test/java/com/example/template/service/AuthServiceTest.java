package com.example.template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.template.config.JwtUtil;
import com.example.template.model.Role;
import com.example.template.model.SubscriptionPlan;
import com.example.template.model.entity.User;
import com.example.template.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock LoginAuditService loginAuditService;

    @InjectMocks AuthService authService;

    private User user(String username) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$stored-hash");
        u.setRoles(Set.of(Role.USER));
        u.setSubscriptionPlan(SubscriptionPlan.FREE);
        return u;
    }

    @Test
    void login_validCredentials_returnsTokenAndAudits() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice")));
        when(passwordEncoder.matches("pw", "$stored-hash")).thenReturn(true);
        when(jwtUtil.generateToken(eq("alice"), anySet())).thenReturn("jwt-token");

        String token = authService.login("alice", "pw");

        assertEquals("jwt-token", token);
        verify(loginAuditService).recordLogin("alice");
    }

    @Test
    void login_wrongPassword_throwsAndDoesNotIssueToken() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice")));
        when(passwordEncoder.matches("bad", "$stored-hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login("alice", "bad"));
        verify(loginAuditService, never()).recordLogin(anyString());
        verify(jwtUtil, never()).generateToken(anyString(), anySet());
    }

    @Test
    void login_unknownUser_throwsAndStillHashesToPreventEnumeration() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login("ghost", "pw"));
        // A hash comparison runs even for a missing user so timing cannot leak existence.
        verify(passwordEncoder).matches(eq("pw"), anyString());
        verify(loginAuditService, never()).recordLogin(anyString());
    }
}
