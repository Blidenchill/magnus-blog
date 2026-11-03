package com.hubc.magnusblog.content.travel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TravelDestinationRepository extends JpaRepository<TravelDestination, UUID> {

    List<TravelDestination> findByRevisionIdOrderBySortOrderAsc(UUID revisionId);

    void deleteByRevisionId(UUID revisionId);
}
