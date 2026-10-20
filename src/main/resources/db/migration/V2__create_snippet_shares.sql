CREATE TABLE snippet_shares (
    snippet_id UUID NOT NULL REFERENCES snippet_ownership (snippet_id),
    recipient_id TEXT NOT NULL CHECK (recipient_id ~ '[^[:space:]]'),
    PRIMARY KEY (snippet_id, recipient_id)
);

CREATE INDEX snippet_shares_recipient_idx ON snippet_shares (recipient_id);

CREATE INDEX snippet_ownership_owner_idx ON snippet_ownership (owner_id);