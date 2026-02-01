package com.onescale.auth.repository;

import com.onescale.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNumber(String mobileNumber);

    boolean existsByFirebaseUid(String firebaseUid);

    boolean existsByEmail(String email);

    boolean existsByMobileNumber(String mobileNumber);
}
