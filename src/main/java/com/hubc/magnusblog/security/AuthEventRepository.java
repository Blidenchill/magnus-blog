package com.hubc.magnusblog.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthEventRepository extends JpaRepository<AuthEvent, UUID> {
}
