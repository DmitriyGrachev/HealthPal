package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.Role;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuthorityTest {

    @Test
    void authoritiesUseSpringSecurityRolePrefix() {
        User user = new User();
        user.setRoles(Set.of(Role.USER, Role.ADMIN));

        assertThat(user.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void roleEnumContainsVipRoleUsedBySecurityConfig() {
        assertThat(Role.values())
                .extracting(Enum::name)
                .contains("VIP");
    }
}
