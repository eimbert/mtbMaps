package com.paygoon.repository;

import com.paygoon.model.AppUser;
import com.paygoon.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);
    List<VerificationToken> findAllByUserAndUsedAtIsNullAndRevokedAtIsNull(AppUser user);
}
