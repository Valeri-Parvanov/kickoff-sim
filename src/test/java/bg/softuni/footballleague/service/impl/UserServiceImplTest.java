package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.ProfileDto;
import bg.softuni.footballleague.dto.RegisterDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void register_usernameTaken_throwsUsernameAlreadyExistsException() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerDto("alice", "pass")))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessageContaining("alice");
    }

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

    @Test
    void updateProfile_setsEmailAndSaves() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("alice@example.com", user.getId())).thenReturn(false);

        ProfileDto dto = new ProfileDto();
        dto.setEmail("alice@example.com");
        userService.updateProfile("alice", dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void updateProfile_emailAlreadyTaken_throwsIllegalArgumentException() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("taken@example.com", user.getId())).thenReturn(true);

        ProfileDto dto = new ProfileDto();
        dto.setEmail("taken@example.com");

        assertThatThrownBy(() -> userService.updateProfile("alice", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taken@example.com");
    }

    @Test
    void changeRole_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeRole(id, Role.ADMIN, "admin"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void changeRole_sameRole_doesNotSave() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.changeRole(id, Role.USER, "admin");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changeRole_lastAdmin_cannotDemoteThrowsIllegalStateException() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.changeRole(id, Role.USER, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last administrator");
    }

    @Test
    void changeRole_promoteToAdmin_savesUserWithNewRole() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.changeRole(id, Role.ADMIN, "superAdmin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    private RegisterDto registerDto(String username, String password) {
        RegisterDto dto = new RegisterDto();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setConfirmPassword(password);
        return dto;
    }
}
