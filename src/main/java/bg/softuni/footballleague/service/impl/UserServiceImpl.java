package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.RegisterDto;
import bg.softuni.footballleague.exception.StaleSessionException;
import bg.softuni.footballleague.exception.UsernameAlreadyExistsException;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.repository.UserRepository;
import bg.softuni.footballleague.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
