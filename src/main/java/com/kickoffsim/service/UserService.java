package com.kickoffsim.service;

import com.kickoffsim.dto.ProfileDto;
import com.kickoffsim.dto.RegisterDto;
import com.kickoffsim.model.Role;
import com.kickoffsim.model.User;
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
