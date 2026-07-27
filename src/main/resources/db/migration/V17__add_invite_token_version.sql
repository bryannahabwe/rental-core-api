-- Revocable invite links: each user carries the version of their currently
-- valid invite token. Issuing or re-sending an invite rotates this value, so
-- any previously issued link stops working. Null for users who never went
-- through the invite flow (and cleared once an invite is accepted).

ALTER TABLE users ADD COLUMN invite_token_version UUID;
