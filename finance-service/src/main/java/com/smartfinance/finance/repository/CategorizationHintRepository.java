package com.smartfinance.finance.repository;

import com.smartfinance.finance.entity.CategorizationHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CategorizationHintRepository extends JpaRepository<CategorizationHint, UUID> {

    Optional<CategorizationHint> findByUserIdAndDescriptionPattern(UUID userId, String descriptionPattern);

    /**
     * Encontra o melhor hint para uma descrição normalizada.
     *
     * Estratégia de match (por prioridade):
     *   1. Exact match — descrição é exatamente igual ao padrão guardado
     *   2. Contains match — a descrição contém o padrão (ex: padrão "PayPal" em "Débito Direto PayPal Europe")
     *
     * Dentro de cada nível de prioridade, prefere o hint com mais ocorrências
     * e o mais recentemente visto.
     *
     * Nota: o match do tipo "padrão contém a descrição" é omitido intencionalmente
     * pois gera falsos positivos quando o padrão é mais longo que o input.
     */
    @Query("""
            SELECT h FROM CategorizationHint h
            WHERE h.userId = :userId
              AND (
                  h.descriptionPattern = :description
                  OR :description LIKE CONCAT('%', h.descriptionPattern, '%')
              )
            ORDER BY
              CASE WHEN h.descriptionPattern = :description THEN 0 ELSE 1 END,
              h.occurrenceCount DESC,
              h.lastSeenAt DESC
            LIMIT 1
            """)
    Optional<CategorizationHint> findBestMatch(
            @Param("userId") UUID userId,
            @Param("description") String description);
}
