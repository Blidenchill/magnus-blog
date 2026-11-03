package com.hubc.magnusblog.content.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhotoItemRepository extends JpaRepository<PhotoItem, UUID> {

    List<PhotoItem> findByRevisionIdOrderBySortOrderAsc(UUID revisionId);

    void deleteByRevisionId(UUID revisionId);
}
