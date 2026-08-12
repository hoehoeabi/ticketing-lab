package com.ticketing.ticketing_lab.domain.user.repository;

import com.ticketing.ticketing_lab.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}
