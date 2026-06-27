package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.RegisterDto;
import bg.softuni.footballleague.exception.UsernameAlreadyExistsException;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.repository.UserRepository;
import bg.softuni.footballleague.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void register(RegisterDto registerDto) {
        if (userRepository.existsByUsername(registerDto.getUsername())) {
            throw new UsernameAlreadyExistsException(
                    "Username %s is already taken".formatted(registerDto.getUsername()));
        }

        User user = new User();
        user.setUsername(registerDto.getUsername());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        user.setRole(Role.USER);

        userRepository.save(user);
    }
}
