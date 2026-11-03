package com.hubc.magnusblog.media;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    Page<MediaAsset> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
