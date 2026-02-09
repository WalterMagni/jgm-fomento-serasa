CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_number VARCHAR(14) NOT NULL UNIQUE,
    name VARCHAR(500),
    email VARCHAR(255),
    phones JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clients_document_number ON clients(document_number);
