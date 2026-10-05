CREATE TABLE snippet_ownership (
    snippet_id UUID PRIMARY KEY,
    owner_id TEXT NOT NULL CHECK (owner_id ~ '[^[:space:]]')
);
