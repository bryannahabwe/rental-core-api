-- Security-deposit settlement recorded at move-out. Nullable: null means the
-- deposit has not been settled. When set, the three parts sum to deposit_amount.
ALTER TABLE rental_agreements ADD COLUMN deposit_applied DECIMAL(12, 2);
ALTER TABLE rental_agreements ADD COLUMN deposit_refunded DECIMAL(12, 2);
ALTER TABLE rental_agreements ADD COLUMN deposit_forfeited DECIMAL(12, 2);
