CREATE TABLE group_members (
  id BIGSERIAL PRIMARY KEY,
  group_id UUID REFERENCES chat_groups(id),
  user_id UUID REFERENCES users(id),
  joined_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(group_id, user_id)
);
CREATE INDEX idx_group_members_user_id ON group_members(user_id);
