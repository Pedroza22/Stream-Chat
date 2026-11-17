CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id UUID REFERENCES chat_groups(id),
  sender_id UUID REFERENCES users(id),
  content TEXT NOT NULL CHECK(length(content) <= 4000),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  version INT DEFAULT 0
);
CREATE INDEX idx_messages_group_id_created_at ON messages(group_id, created_at);
