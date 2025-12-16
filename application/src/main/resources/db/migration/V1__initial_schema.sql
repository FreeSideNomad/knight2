-- =====================================================
-- KNIGHT PLATFORM - CONSOLIDATED SCHEMA
-- Local development only - clean slate migration
-- =====================================================

-- Clients (with address, no taxId/phoneNumber/emailAddress)
CREATE TABLE clients (
    client_id NVARCHAR(100) PRIMARY KEY,
    name NVARCHAR(255) NOT NULL,
    client_type NVARCHAR(20) NOT NULL,
    status NVARCHAR(20) NOT NULL,
    -- Address fields
    address_line1 NVARCHAR(255),
    address_line2 NVARCHAR(255),
    city NVARCHAR(100),
    state_province NVARCHAR(100),
    zip_postal_code NVARCHAR(20),
    country_code NVARCHAR(10),
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL
);

CREATE INDEX idx_clients_name ON clients(name);
CREATE INDEX idx_clients_type ON clients(client_type);

-- Client Accounts
CREATE TABLE client_accounts (
    account_id NVARCHAR(100) PRIMARY KEY,
    client_id NVARCHAR(100) NOT NULL,
    account_system NVARCHAR(50) NOT NULL,
    account_type NVARCHAR(50) NOT NULL,
    currency NVARCHAR(3) NOT NULL,
    status NVARCHAR(20) NOT NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    CONSTRAINT FK_client_accounts_client FOREIGN KEY (client_id)
        REFERENCES clients(client_id)
);

CREATE INDEX idx_client_accounts_client ON client_accounts(client_id);

-- Profiles (no client_id column - derived from profile_client_enrollments)
CREATE TABLE profiles (
    profile_id NVARCHAR(200) PRIMARY KEY,
    name NVARCHAR(255) NOT NULL,
    profile_type NVARCHAR(20) NOT NULL,
    status NVARCHAR(20) NOT NULL,
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL
);

CREATE INDEX idx_profiles_type ON profiles(profile_type);
CREATE INDEX idx_profiles_name ON profiles(name);

-- Profile Client Enrollments (exactly one must have is_primary=true per profile)
CREATE TABLE profile_client_enrollments (
    id NVARCHAR(100) PRIMARY KEY,
    profile_id NVARCHAR(200) NOT NULL,
    client_id NVARCHAR(100) NOT NULL,
    is_primary BIT NOT NULL DEFAULT 0,
    account_enrollment_type NVARCHAR(20) NOT NULL,
    enrolled_at DATETIME2 NOT NULL,
    CONSTRAINT FK_profile_client_enrollments_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id),
    CONSTRAINT UQ_profile_client_enrollments UNIQUE(profile_id, client_id)
);

CREATE INDEX idx_profile_client_enrollments_profile ON profile_client_enrollments(profile_id);
CREATE INDEX idx_profile_client_enrollments_client ON profile_client_enrollments(client_id);
CREATE INDEX idx_profile_client_enrollments_primary ON profile_client_enrollments(client_id, is_primary);

-- Service Enrollments
CREATE TABLE service_enrollments (
    enrollment_id NVARCHAR(100) PRIMARY KEY,
    profile_id NVARCHAR(200) NOT NULL,
    service_type NVARCHAR(100) NOT NULL,
    configuration NVARCHAR(MAX),
    status NVARCHAR(20) NOT NULL,
    enrolled_at DATETIME2 NOT NULL,
    CONSTRAINT FK_service_enrollments_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id)
);

CREATE INDEX idx_service_enrollments_profile ON service_enrollments(profile_id);

-- Account Enrollments (service_enrollment_id nullable for profile-level enrollments)
CREATE TABLE account_enrollments (
    enrollment_id NVARCHAR(100) PRIMARY KEY,
    profile_id NVARCHAR(200) NOT NULL,
    service_enrollment_id NVARCHAR(100),
    client_id NVARCHAR(100) NOT NULL,
    account_id NVARCHAR(100) NOT NULL,
    status NVARCHAR(20) NOT NULL,
    enrolled_at DATETIME2 NOT NULL,
    CONSTRAINT FK_account_enrollments_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id),
    CONSTRAINT FK_account_enrollments_service FOREIGN KEY (service_enrollment_id)
        REFERENCES service_enrollments(enrollment_id)
);

CREATE INDEX idx_account_enrollments_profile ON account_enrollments(profile_id);
CREATE INDEX idx_account_enrollments_service ON account_enrollments(service_enrollment_id);
CREATE INDEX idx_account_enrollments_account ON account_enrollments(account_id);
CREATE INDEX idx_account_enrollments_client ON account_enrollments(client_id);

-- Users (linked to Profile via profile_id)
CREATE TABLE users (
    user_id NVARCHAR(100) PRIMARY KEY,
    email NVARCHAR(255) NOT NULL UNIQUE,
    user_type NVARCHAR(20) NOT NULL,
    identity_provider NVARCHAR(20) NOT NULL,
    profile_id NVARCHAR(200) NOT NULL,
    status NVARCHAR(20) NOT NULL,
    lock_reason NVARCHAR(500),
    deactivation_reason NVARCHAR(500),
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_profile ON users(profile_id);
