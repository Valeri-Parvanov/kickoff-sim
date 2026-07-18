package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.ProfileDto;
import bg.softuni.footballleague.dto.RegisterDto;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface UserService {

    Role register(RegisterDto registerDto);

    User findByUsername(String username);

    void updateProfile(String currentUsername, ProfileDto dto);

    Page<User> findAllPaged(int page, int size);

    long countByRole(Role role);

    void changeRole(UUID userId, Role newRole, String adminUsername);
}
