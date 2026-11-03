package com.hubc.magnusblog.content.food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "recipe_ingredient")
public class RecipeIngredient {

    @Id
    private UUID id;

    @Column(name = "revision_id", nullable = false)
    private UUID revisionId;

    @Column(nullable = false, length = 120)
    private String name = "";

    @Column(length = 120)
    private String amount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(UUID revisionId) {
        this.revisionId = revisionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
