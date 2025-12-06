-- Migration V1008: Add temporary password hash to convite table
-- Support for temporary password + Keycloak required action flow
--
-- Flow:
-- 1. Admin invites user → backend generates random temporary password (12 chars)
-- 2. Backend hashes password with BCrypt → stores hash in this column
-- 3. Email sent with temporary password in plain text
-- 4. User activates account with token + temporary password
-- 5. Backend validates: BCrypt.matches(providedPassword, stored_hash)
-- 6. If valid: create Keycloak user with UPDATE_PASSWORD required action
-- 7. User must change password on first login (Keycloak enforces policy)
--
-- Security:
-- - Temporary password is cryptographically random (SecureRandom)
-- - Hash stored with BCrypt (adaptive, salted, slow)
-- - Plain password NEVER stored, only sent in email once
-- - Token + temporary password both required for activation
-- - Temporary password invalidated after successful activation
--
-- Author: Jetski Team
-- Date: 2025-10-23

ALTER TABLE convite
ADD COLUMN temporary_password_hash VARCHAR(255);

COMMENT ON COLUMN convite.temporary_password_hash IS
'BCrypt hash ($2a$ prefix, 60 chars) of temporary password sent in invitation email. Used to validate activation before Keycloak takes over password management with UPDATE_PASSWORD required action.';
