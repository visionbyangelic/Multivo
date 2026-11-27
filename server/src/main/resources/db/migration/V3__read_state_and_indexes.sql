-- Tracks when each participant last opened a conversation (used for unread counts).
ALTER TABLE conversation_participants
    ADD COLUMN IF NOT EXISTS last_read_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_connection_requests_sender ON connection_requests(sender_id);
CREATE INDEX IF NOT EXISTS idx_app_users_username_lower ON app_users(LOWER(username));
CREATE INDEX IF NOT EXISTS idx_app_users_email_lower ON app_users(LOWER(email));
