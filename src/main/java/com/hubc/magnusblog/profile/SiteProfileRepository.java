package com.hubc.magnusblog.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SiteProfileRepository extends JpaRepository<SiteProfile, UUID> {
}
