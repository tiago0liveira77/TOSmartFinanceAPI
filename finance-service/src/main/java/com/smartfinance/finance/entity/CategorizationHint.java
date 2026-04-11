package com.smartfinance.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "categorization_hints")
@Getter
@Setter
@NoArgsConstructor
public class CategorizationHint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 500)
    private String descriptionPattern;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private int occurrenceCount = 1;

    @UpdateTimestamp
    private LocalDateTime lastSeenAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CategorizationHint(UUID userId, String descriptionPattern, Category category) {
        this.userId = userId;
        this.descriptionPattern = descriptionPattern;
        this.category = category;
    }
}
