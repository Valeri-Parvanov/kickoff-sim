package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.RegisterDto;
import bg.softuni.footballleague.exception.StaleSessionException;
import bg.softuni.footballleague.exception.UsernameAlreadyExistsException;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // -----------------------------------------------------------------------
    // First user gets ADMIN
    // -----------------------------------------------------------------------

    @Test
    void register_firstUser_getsAdminRole() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.count()).thenReturn(0L);

        Role role = userService.register(registerDto("alice", "password123"));

        assertThat(role).isEqualTo(Role.ADMIN);
    }

    @Test
    void register_secondUser_getsUserRole() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.count()).thenReturn(1L);

        Role role = userService.register(registerDto("bob", "password123"));

        assertThat(role).isEqualTo(Role.USER);
    }

    // -----------------------------------------------------------------------
    // Password is encoded
    // -----------------------------------------------------------------------

    @Test
    void register_passwordIsEncoded() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.count()).thenReturn(0L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.register(registerDto("alice", "plaintext"));

        assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
        assertThat(captor.getValue().getPassword()).isNotEqualTo("plaintext");
    }

    // -----------------------------------------------------------------------
    // Username taken
    // -----------------------------------------------------------------------

    @Test
    void register_usernameTaken_throwsUsernameAlreadyExistsException() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerDto("alice", "pass")))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessageContaining("alice");
    }

    // -----------------------------------------------------------------------
    // findByUsername
    // -----------------------------------------------------------------------

    @Test
    void findByUsername_exists_returnsUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        User found = userService.findByUsername("alice");

        assertThat(found.getUsername()).isEqualTo("alice");
    }

    @Test
    void findByUsername_notFound_throwsStaleSessionException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByUsername("ghost"))
                .isInstanceOf(StaleSessionException.class)
                .hasMessageContaining("ghost");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private RegisterDto registerDto(String username, String password) {
        RegisterDto dto = new RegisterDto();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setConfirmPassword(password);
        return dto;
    }
}
