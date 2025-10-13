-- Enable pgvector for semantic search
CREATE EXTENSION IF NOT EXISTS vector;

-- Store all Gmail & HubSpot extracted text + embeddings for RAG
CREATE TABLE embeddings (
    id SERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    source VARCHAR(50) NOT NULL,           -- e.g. 'gmail', 'hubspot', 'calendar'
    source_id VARCHAR(255) NOT NULL,       -- unique message/contact/event id
    title TEXT,
    content TEXT NOT NULL,
    embedding vector(1536),                -- OpenAI embedding dimension
    created_at TIMESTAMP DEFAULT NOW()
);

-- Persist long-running / tool-called tasks
CREATE TABLE tasks (
    id SERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'PENDING',  -- PENDING, RUNNING, DONE, FAILED
    context JSONB,                         -- store any runtime data
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Store persistent agent “rules” or ongoing instructions
CREATE TABLE memories (
    id SERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    instruction TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Store OAuth credentials for Google & HubSpot
CREATE TABLE oauth_tokens (
    id SERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,         -- 'google' or 'hubspot'
    access_token TEXT NOT NULL,
    refresh_token TEXT,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for faster lookups
CREATE INDEX idx_embeddings_user_email ON embeddings(user_email);
CREATE INDEX idx_embeddings_source ON embeddings(source);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_memories_user_email ON memories(user_email);
