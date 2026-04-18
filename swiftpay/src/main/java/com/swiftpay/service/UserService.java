package com.swiftpay.service;

import com.swiftpay.dto.CreateUserRequest;
import com.swiftpay.dto.UserResponse;
import com.swiftpay.exception.ApiException;
import com.swiftpay.model.User;
import com.swiftpay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsById(request.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "User id already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email already exists");
        }
        if (userRepository.existsByMobileNumber(request.getMobileNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "MOBILE_ALREADY_EXISTS", "Mobile number already exists");
        }
        return toResponse(userRepository.save(toEntity(request)));
    }

    @Transactional
    public void createUserIfMissing(CreateUserRequest request) {
        if (!userRepository.existsById(request.getId())) {
            userRepository.save(toEntity(request));
        }
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(String userId) {
        return userRepository.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private User toEntity(CreateUserRequest request) {
        User user = new User();
        user.setId(request.getId());
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setMobileNumber(request.getMobileNumber());
        user.setBalance(request.getBalance());
        user.setCurrency(request.getCurrency().toUpperCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        return user;
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .balance(user.getBalance())
                .currency(user.getCurrency())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
