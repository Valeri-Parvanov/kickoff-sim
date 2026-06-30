package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.RegisterDto;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;

public interface UserService {

    Role register(RegisterDto registerDto);

    User findByUsername(String username);
}
