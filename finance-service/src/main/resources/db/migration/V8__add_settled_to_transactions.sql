ALTER TABLE transactions ADD COLUMN settled BOOLEAN NOT NULL DEFAULT false;

-- Marcar como settled todas as transações existentes com data <= hoje
-- (foram criadas antes desta feature, portanto o saldo já as contém)
UPDATE transactions SET settled = true WHERE date <= CURRENT_DATE AND deleted_at IS NULL;

CREATE INDEX idx_transactions_settled ON transactions(settled) WHERE settled = false;
