package com.hubc.magnusblog.media;

import com.hubc.magnusblog.common.VariantType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaVariantRepository extends JpaRepository<MediaVariant, UUID> {

    Optional<MediaVariant> findByMediaIdAndVariantType(UUID mediaId, VariantType variantType);

    List<MediaVariant> findByMediaId(UUID mediaId);

    List<MediaVariant> findByMediaIdIn(List<UUID> mediaIds);
}
