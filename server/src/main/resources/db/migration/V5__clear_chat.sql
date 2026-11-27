-- 27 Nov 2026: clear chat — each person can wipe a conversation from
-- their own view without deleting it for anyone else. cleared_at marks
-- the moment they cleared; messages up to then are hidden from them, and
-- anything sent after still arrives.
ALTER TABLE conversation_participants ADD COLUMN IF NOT EXISTS cleared_at TIMESTAMPTZ;
