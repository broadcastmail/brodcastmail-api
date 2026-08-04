-- nullable: only populated going forward, when a Resend webhook is
-- auto-registered during onboarding; existing rows predate this feature
ALTER TABLE email_providers
    ADD COLUMN encrypted_webhook_secret TEXT,
    ADD COLUMN resend_webhook_id        TEXT;
