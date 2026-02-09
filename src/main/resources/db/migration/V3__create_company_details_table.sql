CREATE TABLE company_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_number VARCHAR(14) NOT NULL UNIQUE,
    updated_at TIMESTAMP,
    alias VARCHAR(255),
    founded DATE,
    company_name VARCHAR(500),
    company_equity DECIMAL(20, 2),
    nature_id INTEGER,
    nature_text VARCHAR(255),
    size_text VARCHAR(100),
    street VARCHAR(255),
    number VARCHAR(50),
    details VARCHAR(255),
    district VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(10),
    zip VARCHAR(20),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    members JSONB,
    phones JSONB,
    emails JSONB,
    main_activity JSONB,
    side_activities JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_company_details_document_number ON company_details(document_number);
CREATE INDEX idx_company_details_city ON company_details(city);
