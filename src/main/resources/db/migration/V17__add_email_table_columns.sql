ALTER TABLE connections
    ADD COLUMN IF NOT EXISTS email_table_schema VARCHAR,
    ADD COLUMN IF NOT EXISTS email_table_name VARCHAR,
    ADD COLUMN IF NOT EXISTS email_table_id_column VARCHAR;
