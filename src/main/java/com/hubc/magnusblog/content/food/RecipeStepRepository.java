package com.hubc.magnusblog.content.food;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, UUID> {

    List<RecipeStep> findByRevisionIdOrderBySortOrderAsc(UUID revisionId);

    void deleteByRevisionId(UUID revisionId);
}
