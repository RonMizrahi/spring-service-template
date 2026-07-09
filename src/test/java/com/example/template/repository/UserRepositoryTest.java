package com.example.template.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.template.model.Role;
import com.example.template.model.SubscriptionPlan;
import com.example.template.model.entity.User;

/**
 * Repository slice test: boots only the JPA layer against an embedded database,
 * demonstrating {@code @DataJpaTest} rather than a full application context.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsername_returnsSavedUserWithRolesAndPlan() {
        User u = new User();
        u.setUsername("repo-user");
        u.setPassword("hashed");
        u.setRoles(Set.of(Role.ADMIN, Role.USER));
        u.setSubscriptionPlan(SubscriptionPlan.BASIC);
        userRepository.save(u);

        Optional<User> found = userRepository.findByUsername("repo-user");

        assertThat(found).isPresent();
        assertThat(found.get().getRoles()).containsExactlyInAnyOrder(Role.ADMIN, Role.USER);
        assertThat(found.get().getSubscriptionPlan()).isEqualTo(SubscriptionPlan.BASIC);
    }

    @Test
    void findByUsername_whenAbsent_returnsEmpty() {
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }
}
