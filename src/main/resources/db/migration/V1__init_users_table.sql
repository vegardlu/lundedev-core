CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Insert the initially authorized user
INSERT INTO users (email, name, role) VALUES ('vegard@lundeberg.cc', 'Vegard Lundeberg', 'ADMIN');
