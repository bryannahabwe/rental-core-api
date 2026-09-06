-- Record which receipt number was issued for a payment.
--
-- The number was drawn from landlord_settings.next_receipt_no by a separate
-- endpoint and never stored anywhere, which left two defects.
--
-- Downloading a receipt drew a NEW number every time, so re-printing one
-- payment's receipt handed the tenant a different number than the copy they
-- already held, and burned a number out of the sequence for each attempt.
--
-- And a payment removed as an error took the link with it: RECORD_PAYMENT and
-- ISSUE_RECEIPT are separate audit rows sharing no key, correlatable only by
-- timestamp and acting user. Reversing a payment tells the landlord the receipt
-- stays issued — as it must, since the tenant may be holding it — without being
-- able to say which one.
--
-- Nullable and not backfilled: for payments already recorded there is no honest
-- value to write, and inventing one would be worse than the gap it fills.
-- Those rows draw and keep a number the next time a receipt is issued.

ALTER TABLE payments ADD COLUMN receipt_no VARCHAR(50);
