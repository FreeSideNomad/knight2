-- Add external_reference column to indirect_clients table
ALTER TABLE indirect_clients ADD external_reference NVARCHAR(100) NULL;
