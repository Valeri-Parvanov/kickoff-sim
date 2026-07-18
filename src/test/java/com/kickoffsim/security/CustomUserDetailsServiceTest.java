package com.kickoffsim.security;

import com.kickoffsim.model.Role;
import com.kickoffsim.model.User;
import com.kickoffsim.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_existingUser_returnsUserDetailsWithRoleAuthority() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setPassword("encodedPass");
        user.setRole(Role.ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("encodedPass");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_notFound_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void loadUserByUsername_userRoleUser_returnsRoleUserAuthority() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("bob");
        user.setPassword("pass");
        user.setRole(Role.USER);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("bob");

        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }
}
