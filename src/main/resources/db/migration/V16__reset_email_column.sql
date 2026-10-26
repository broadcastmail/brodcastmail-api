-- null now means "use auth.user_emails"; non-null means a user-picked profile table column.
-- Existing connections were created with the hardcoded default 'email' which always resolved
-- to auth.user_emails — reset them so the new null convention holds.
UPDATE connections SET email_column = NULL WHERE email_column = 'email';
