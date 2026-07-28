package com.kickoffsim.service;

import com.kickoffsim.dto.ChangePasswordDto;
import com.kickoffsim.dto.ProfileDto;
import com.kickoffsim.dto.RegisterDto;
import com.kickoffsim.model.Role;
import com.kickoffsim.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.UUID;

public interface UserService {

    Role register(RegisterDto registerDto);

    User findByUsername(String username);

    void updateProfile(String currentUsername, ProfileDto dto);

    void changePassword(String currentUsername, ChangePasswordDto dto);

    Page<User> findAllPaged(int page, int size, Sort sort);

    long countByRole(Role role);

    void changeRole(UUID userId, Role newRole, String adminUsername);

    void deactivateSelf(String username, String rawPassword);

    void setEnabled(UUID userId, boolean enabled, String actingUsername);

    void recordLoginFailure(String username);

    void recordLoginSuccess(String username);
}
