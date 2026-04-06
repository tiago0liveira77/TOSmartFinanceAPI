package com.smartfinance.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount; // sempre positivo

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(length = 500)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private boolean isRecurring = false;

    /** true = o saldo da conta já foi ajustado para esta transação */
    @Column(nullable = false)
    private boolean settled = false;

    @Enumerated(EnumType.STRING)
    private RecurrenceRule recurrenceRule;

    @Column(nullable = false)
    private boolean aiCategorized = false;

    @Column(precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    private UUID importId;

    private UUID recurrenceGroupId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}