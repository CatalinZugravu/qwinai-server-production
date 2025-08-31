-- Migration: Add Ad Credits Support to user_states table
-- Date: 2025-08-31
-- Description: Add columns to track ad credits earned per day per credit type

-- Add columns for ad credits tracking
ALTER TABLE user_states 
ADD COLUMN IF NOT EXISTS ad_credits_chat_today INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS ad_credits_image_today INTEGER DEFAULT 0;

-- Add index for efficient queries
CREATE INDEX IF NOT EXISTS idx_user_states_ad_credits 
ON user_states(ad_credits_chat_today, ad_credits_image_today);

-- Add comments for documentation
COMMENT ON COLUMN user_states.ad_credits_chat_today IS 'Number of ad credits earned today for chat (max 10 per day)';
COMMENT ON COLUMN user_states.ad_credits_image_today IS 'Number of ad credits earned today for images (max 10 per day)';

-- Verify the migration
SELECT column_name, data_type, column_default, is_nullable
FROM information_schema.columns
WHERE table_name = 'user_states' 
  AND column_name IN ('ad_credits_chat_today', 'ad_credits_image_today')
ORDER BY column_name;

-- Test query to ensure the new columns work
SELECT 
    device_id,
    credits_consumed_today_chat,
    ad_credits_chat_today,
    (15 + COALESCE(ad_credits_chat_today, 0) - credits_consumed_today_chat) AS available_chat_credits,
    credits_consumed_today_image,
    ad_credits_image_today,
    (20 + COALESCE(ad_credits_image_today, 0) - credits_consumed_today_image) AS available_image_credits
FROM user_states 
LIMIT 5;