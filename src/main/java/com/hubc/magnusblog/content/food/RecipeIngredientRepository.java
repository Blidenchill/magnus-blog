package com.hubc.magnusblog.content.food;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, UUID> {

    List<RecipeIngredient> findByRevisionIdOrderBySortOrderAsc(UUID revisionId);

    void deleteByRevisionId(UUID revisionId);
}
