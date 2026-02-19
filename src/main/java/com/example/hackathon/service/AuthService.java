package com.example.hackathon.service;

import com.example.hackathon.dto.AuthResponse;
import com.example.hackathon.dto.FacultyLoginRequest;
import com.example.hackathon.dto.FacultyRegisterRequest;
import com.example.hackathon.dto.LoginRequest;
import com.example.hackathon.dto.RegisterRequest;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.model.Role;
import com.example.hackathon.model.User;
import com.example.hackathon.repository.UserRepository;
import com.example.hackathon.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final String facultySecretCode;

    public AuthService(UserRepository userRepository,
                       UserService userService,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       @Value("${app.faculty.secret-code:KLHAZ}") String facultySecretCode) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.facultySecretCode = facultySecretCode;
    }

    public AuthResponse registerUser(RegisterRequest request) {
        return registerWithRole(request, Role.USER);
    }

    public AuthResponse registerFaculty(FacultyRegisterRequest request) {
        if (!facultySecretCode.equals(request.secretCode())) {
            throw new BadRequestException("Invalid faculty secret code");
        }
        RegisterRequest registerRequest = new RegisterRequest(request.name(), request.email(), request.password());
        return registerWithRole(registerRequest, Role.FACULTY);
    }

    public AuthResponse login(LoginRequest request) {
        String email = userService.normalizeEmail(request.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        User user = userService.findByEmail(email);
        if (user.getRole() == Role.FACULTY) {
            throw new BadRequestException("Faculty must use the faculty login page with secret code");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse facultyLogin(FacultyLoginRequest request) {
        if (!facultySecretCode.equals(request.secretCode())) {
            throw new BadRequestException("Invalid faculty secret code");
        }

        String email = userService.normalizeEmail(request.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        User user = userService.findByEmail(email);
        if (user.getRole() != Role.FACULTY) {
            throw new BadRequestException("Only faculty accounts can use this login");
        }

        return buildAuthResponse(user);
    }

    public User ensureFacultyUser(String name, String email, String rawPassword) {
        String normalizedEmail = userService.normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            return userService.findByEmail(normalizedEmail);
        }

        User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.FACULTY);
        return userRepository.save(user);
    }

    private AuthResponse registerWithRole(RegisterRequest request, Role role) {
        String normalizedEmail = userService.normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("Email is already registered");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("Email is already registered");
        }

        return buildAuthResponse(saved);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        user.getPassword(),
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                ),
                Map.of("role", user.getRole().name(), "userId", user.getId())
        );

        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }
}
