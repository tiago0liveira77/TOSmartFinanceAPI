-- Tabela de hints de categorização baseados em histórico do utilizador.
-- Alimentada quando o utilizador:
--   1. Seleciona manualmente uma categoria no modal de preview de CSV
--   2. Corrige a categoria de uma transação existente
--
-- O ai-service consulta esta tabela antes de chamar a API de AI,
-- evitando chamadas redundantes para padrões já conhecidos.

CREATE TABLE categorization_hints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    description_pattern VARCHAR(500) NOT NULL,   -- descrição normalizada usada como chave de lookup
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    occurrence_count INT NOT NULL DEFAULT 1,     -- quantas vezes este par foi confirmado
    last_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_hints_user_pattern
    ON categorization_hints(user_id, description_pattern);

CREATE INDEX idx_hints_category
    ON categorization_hints(category_id);
