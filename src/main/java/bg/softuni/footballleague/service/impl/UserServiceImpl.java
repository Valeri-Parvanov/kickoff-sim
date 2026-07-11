package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.ProfileDto;
import bg.softuni.footballleague.dto.RegisterDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.StaleSessionException;
import bg.softuni.footballleague.exception.UsernameAlreadyExistsException;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.repository.UserRepository;
import bg.softuni.footballleague.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Role register(RegisterDto registerDto) {
        if (userRepository.existsByUsername(registerDto.getUsername())) {
            throw new UsernameAlreadyExistsException(
                    "Username %s is already taken".formatted(registerDto.getUsername()));
        }

        User user = new User();
        user.setUsername(registerDto.getUsername());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        user.setRole(userRepository.count() == 0 ? Role.ADMIN : Role.USER);

        userRepository.save(user);
        log.info("Registered user '{}' with role {}", user.getUsername(), user.getRole());
        return user.getRole();
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new StaleSessionException(
                        "Your session refers to a user (%s) that no longer exists".formatted(username)));
    }

    @Override
    @Transactional
    public void updateProfile(String currentUsername, ProfileDto dto) {
        User user = findByUsername(currentUsername);

        String email = (dto.getEmail() == null || dto.getEmail().isBlank()) ? null : dto.getEmail().trim();
        if (email != null && userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new IllegalArgumentException("Email '" + email + "' is already in use by another account.");
        }

        user.setEmail(email);
        userRepository.save(user);
        log.info("Profile updated for user '{}'", currentUsername);
    }

    @Override
    public Page<User> findAllPaged(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size, Sort.by("username")));
    }

    @Override
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    @Override
    @Transactional
    public void changeRole(UUID userId, Role newRole, String adminUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        if (user.getRole() == newRole) return;

        if (user.getRole() == Role.ADMIN && newRole == Role.USER) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalStateException(
                        "Cannot demote the last administrator. Promote another user to admin first.");
            }
        }

        user.setRole(newRole);
        userRepository.save(user);
        log.info("User '{}' role changed to {} by '{}'", user.getUsername(), newRole, adminUsername);
    }
}
