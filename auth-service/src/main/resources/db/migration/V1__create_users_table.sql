CREATE TABLE users (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     email VARCHAR(255) UNIQUE NOT NULL,
     password VARCHAR(255),
     name VARCHAR(255) NOT NULL,
     avatar_url TEXT,
     provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
     email_verified BOOLEAN NOT NULL DEFAULT FALSE,
     created_at TIMESTAMP NOT NULL DEFAULT NOW(),
     updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
     deleted_at TIMESTAMP
 );

 CREATE INDEX idx_users_email ON users(email);
 CREATE INDEX idx_users_provider ON users(provider);