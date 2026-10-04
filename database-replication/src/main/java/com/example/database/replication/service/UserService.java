package com.example.database.replication.service;

import com.example.database.replication.dto.CreateUserRequest;
import com.example.database.replication.dto.UpdateUserRequest;
import com.example.database.replication.entity.User;
import com.example.database.replication.repository.UserRepository;
import com.example.database.replication.routing.UseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @UseWriter
    @Transactional
    public User createUser(CreateUserRequest request) {
        var user = User.builder().name(request.name()).email(request.email()).build();
        return userRepository.save(user);
    }

    /**
     * Read-after-write: Use writer for immediate consistency post-create.
     * This ensures the newly created user is visible before returning to the client.
     */
    @UseWriter
    @Transactional
    public Optional<User> findByIdAfterWrite(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Regular read: Uses reader datasource for scalability.
     */
    @Retryable(
            includes = {SQLException.class, DataAccessException.class},
            maxRetries = 4,
            delay = 1000,
            multiplier = 2
    )
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> findByName(String name) {
        return userRepository.findByName(name);
    }

    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @UseWriter
    @Transactional
    public User updateUser(Long id, UpdateUserRequest request) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User with id " + id + " not found"));
        user.setName(request.name());
        user.setEmail(request.email());
        return userRepository.save(user);
    }

    @UseWriter
    @Transactional
    public void deleteById(Long id) {
        if (!userRepository.existsById(id)) {
            throw new jakarta.persistence.EntityNotFoundException("User with id " + id + " not found");
        }
        userRepository.deleteById(id);
    }
}
