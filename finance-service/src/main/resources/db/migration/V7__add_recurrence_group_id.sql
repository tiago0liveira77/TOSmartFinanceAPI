ALTER TABLE transactions ADD COLUMN recurrence_group_id UUID;

CREATE INDEX idx_transactions_recurrence_group_id ON transactions(recurrence_group_id);
