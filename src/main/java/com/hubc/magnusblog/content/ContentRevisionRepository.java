package com.hubc.magnusblog.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContentRevisionRepository extends JpaRepository<ContentRevision, UUID> {
}
