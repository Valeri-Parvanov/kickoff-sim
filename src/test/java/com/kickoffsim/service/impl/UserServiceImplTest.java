package com.kickoffsim.service.impl;

import com.kickoffsim.dto.ChangePasswordDto;
import com.kickoffsim.dto.ProfileDto;
import com.kickoffsim.dto.RegisterDto;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.exception.StaleSessionException;
import com.kickoffsim.exception.UsernameAlreadyExistsException;
import com.kickoffsim.model.Role;
import com.kickoffsim.model.User;
import com.kickoffsim.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    void updateProfile_blankEmail_clearsEmailAndSaves() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("old@example.com");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        ProfileDto dto = new ProfileDto();
        dto.setEmail("   ");
        userService.updateProfile("alice", dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
    }

    @Test
    void updateProfile_nullEmail_clearsEmailAndSaves() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("old@example.com");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        ProfileDto dto = new ProfileDto();

        userService.updateProfile("alice", dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
    }

    @Test
    void changePassword_correctCurrentPassword_updatesAndSaves() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setPassword("oldEncoded");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "oldEncoded")).thenReturn(true);
        when(passwordEncoder.encode("newpass1")).thenReturn("newEncoded");

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("old");
        dto.setNewPassword("newpass1");
        dto.setConfirmPassword("newpass1");
        userService.changePassword("alice", dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("newEncoded");
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsIllegalArgumentException() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setPassword("oldEncoded");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "oldEncoded")).thenReturn(false);

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("wrong");
        dto.setNewPassword("newpass1");
        dto.setConfirmPassword("newpass1");

        assertThatThrownBy(() -> userService.changePassword("alice", dto))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void findAllPaged_delegatesToRepositoryWithSortedPageRequest() {
        Page<User> page = new PageImpl<>(java.util.List.of(new User()));
        when(userRepository.findAll(PageRequest.of(0, 10, Sort.by("username")))).thenReturn(page);

        Page<User> result = userService.findAllPaged(0, 10);

        assertThat(result).isSameAs(page);
    }

    @Test
    void countByRole_delegatesToRepository() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        assertThat(userService.countByRole(Role.ADMIN)).isEqualTo(2L);
    }

    @Test
    void changeRole_demoteAdmin_notLastAdmin_succeeds() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        userService.changeRole(id, Role.USER, "admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
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

    @Test
    void deactivateSelf_correctPassword_deactivatesAndSaves() {
        User user = new User();
        user.setUsername("alice");
        user.setPassword("encodedPass");
        user.setRole(Role.USER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "encodedPass")).thenReturn(true);

        userService.deactivateSelf("alice", "correct");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
    }

    @Test
    void deactivateSelf_wrongPassword_throwsIllegalArgumentException() {
        User user = new User();
        user.setUsername("alice");
        user.setPassword("encodedPass");
        user.setRole(Role.USER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encodedPass")).thenReturn(false);

        assertThatThrownBy(() -> userService.deactivateSelf("alice", "wrong"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateSelf_lastAdmin_throwsIllegalStateException() {
        User user = new User();
        user.setUsername("admin");
        user.setPassword("encodedPass");
        user.setRole(Role.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "encodedPass")).thenReturn(true);
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deactivateSelf("admin", "correct"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last administrator");
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setEnabled(id, false, "admin"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void setEnabled_alreadyInRequestedState_doesNotSave() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.USER);
        user.setEnabled(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.setEnabled(id, true, "admin");

        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_disableRegularUser_succeeds() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.USER);
        user.setEnabled(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.setEnabled(id, false, "admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
    }

    @Test
    void setEnabled_reactivateUser_succeeds() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.USER);
        user.setEnabled(false);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.setEnabled(id, true, "admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isTrue();
    }

    @Test
    void setEnabled_disableAdmin_notLastAdmin_succeeds() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        userService.setEnabled(id, false, "admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
    }

    @Test
    void setEnabled_disableLastAdmin_throwsIllegalStateException() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.setEnabled(id, false, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last administrator");
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_disableAlreadyDisabledUser_noOp() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.USER);
        user.setEnabled(false);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.setEnabled(id, false, "admin");

        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_reactivateDisabledUser_alsoClearsLock() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.USER);
        user.setEnabled(false);
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.setEnabled(id, true, "admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void setEnabled_reactivateAlreadyEnabledButLockedUser_clearsLockAndSaves() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.setEnabled(id, true, "admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void setEnabled_reactivateAlreadyEnabledButWithFailedAttempts_clearsAttemptsAndSaves() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setFailedLoginAttempts(2);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.setEnabled(id, true, "admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
    }

    @Test
    void recordLoginFailure_incrementsAttempts() {
        User user = new User();
        user.setUsername("alice");
        user.setFailedLoginAttempts(2);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        userService.recordLoginFailure("alice");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(3);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void recordLoginFailure_reachesThreshold_locksAndResetsCounter() {
        User user = new User();
        user.setUsername("alice");
        user.setFailedLoginAttempts(4);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        userService.recordLoginFailure("alice");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNotNull();
        assertThat(captor.getValue().getLockedUntil()).isAfter(java.time.LocalDateTime.now());
    }

    @Test
    void recordLoginFailure_unknownUsername_noOp() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        userService.recordLoginFailure("ghost");

        verify(userRepository, never()).save(any());
    }

    @Test
    void recordLoginSuccess_resetsLockState() {
        User user = new User();
        user.setUsername("alice");
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        userService.recordLoginSuccess("alice");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void recordLoginSuccess_zeroAttemptsButLocked_resetsAndSaves() {
        User user = new User();
        user.setUsername("alice");
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        userService.recordLoginSuccess("alice");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void recordLoginSuccess_alreadyClean_noOp() {
        User user = new User();
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        userService.recordLoginSuccess("alice");

        verify(userRepository, never()).save(any());
    }

    @Test
    void recordLoginSuccess_unknownUsername_noOp() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        userService.recordLoginSuccess("ghost");

        verify(userRepository, never()).save(any());
    }

    private RegisterDto registerDto(String username, String password) {
        RegisterDto dto = new RegisterDto();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setConfirmPassword(password);
        return dto;
    }
}
