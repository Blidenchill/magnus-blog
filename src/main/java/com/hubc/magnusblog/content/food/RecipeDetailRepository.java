package com.hubc.magnusblog.content.food;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecipeDetailRepository extends JpaRepository<RecipeDetail, UUID> {
}
