-- 27 Nov 2026: the app grows up — voice notes, photos, stickers, stories.
-- A message can now carry media alongside (or instead of) text, and stories
-- get their own table. Media is stored as base64 in the row itself: the
-- free database has no object storage attached, and a 60-second voice note
-- or a resized photo is small enough (tens to a few hundred KB) that a TEXT
-- column is honest and simple. The app compresses before it uploads.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS audio_data TEXT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS image_data TEXT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS sticker_id VARCHAR(50);

-- Stories (Moments): a photo and/or a caption in your own language, seen
-- by the people you chat with, gone after 24 hours.
CREATE TABLE IF NOT EXISTS stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    caption TEXT,
    image_data TEXT,
    caption_language VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '24 hours'
);
CREATE INDEX IF NOT EXISTS idx_stories_user ON stories(user_id);
CREATE INDEX IF NOT EXISTS idx_stories_expires ON stories(expires_at);
