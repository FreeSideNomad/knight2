-- Create clients table
CREATE TABLE clients (
    client_id VARCHAR(255) PRIMARY KEY,      -- URN format: srf:123 or cdr:456
    client_type VARCHAR(10) NOT NULL,        -- 'srf' or 'cdr'
    name NVARCHAR(500) NOT NULL,
    address_line1 NVARCHAR(255),
    address_line2 NVARCHAR(255),
    city NVARCHAR(100),
    state_province NVARCHAR(100),
    zip_postal_code VARCHAR(20),
    country_code CHAR(2),
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL
);

-- Create indexes for search
CREATE INDEX idx_clients_type ON clients(client_type);
CREATE INDEX idx_clients_name ON clients(name);
CREATE INDEX idx_clients_type_name ON clients(client_type, name);
