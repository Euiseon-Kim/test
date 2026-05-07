package com.example.ozr.repository;

import com.example.ozr.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserIdAndActiveTrue(String userId);
}
