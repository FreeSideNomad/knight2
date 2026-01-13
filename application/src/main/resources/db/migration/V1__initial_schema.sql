-- =====================================================
-- KNIGHT PLATFORM - CONSOLIDATED SCHEMA
-- Local development only - clean slate migration
-- =====================================================

-- Clients (with address, no taxId/phoneNumber/emailAddress)
CREATE TABLE clients (
    client_id VARCHAR(100) PRIMARY KEY,           -- URN string (e.g., srf:123456)
    name NVARCHAR(255) NOT NULL,                  -- May contain Unicode
    client_type VARCHAR(20) NOT NULL,             -- PERSON, BUSINESS
    status VARCHAR(20) NOT NULL,                  -- ACTIVE, INACTIVE, etc.
    -- Address fields (may contain Unicode)
    address_line1 NVARCHAR(255),
    address_line2 NVARCHAR(255),
    city NVARCHAR(100),
    state_province NVARCHAR(100),
    zip_postal_code VARCHAR(20),
    country_code VARCHAR(10),
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL
);

CREATE INDEX idx_clients_name ON clients(name);
CREATE INDEX idx_clients_type ON clients(client_type);

-- Profiles (no client_id column - derived from profile_client_enrollments)
CREATE TABLE profiles (
    profile_id VARCHAR(200) PRIMARY KEY,          -- URN string
    name NVARCHAR(255) NOT NULL,                  -- May contain Unicode
    profile_type VARCHAR(20) NOT NULL,            -- SERVICING, ONLINE
    status VARCHAR(20) NOT NULL,
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,            -- May contain Unicode
    updated_at DATETIME2 NOT NULL
);

CREATE INDEX idx_profiles_type ON profiles(profile_type);
CREATE INDEX idx_profiles_name ON profiles(name);

-- =====================================================
-- INDIRECT CLIENTS (for Receivable Service)
-- =====================================================

-- Indirect Clients table
CREATE TABLE indirect_clients (
    client_id VARCHAR(50) PRIMARY KEY,            -- URN format: ind:{UUID}
    parent_client_id VARCHAR(100) NOT NULL,       -- URN string (references clients)
    parent_profile_id VARCHAR(200) NOT NULL,      -- URN string (references profiles)
    client_type VARCHAR(20) NOT NULL,             -- PERSON or BUSINESS
    name NVARCHAR(255) NOT NULL,                  -- May contain Unicode
    external_reference VARCHAR(100),              -- External system reference
    status VARCHAR(20) NOT NULL,                  -- PENDING, ACTIVE, SUSPENDED
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT FK_indirect_client_parent FOREIGN KEY (parent_client_id)
        REFERENCES clients(client_id),
    CONSTRAINT FK_indirect_client_profile FOREIGN KEY (parent_profile_id)
        REFERENCES profiles(profile_id)
);

CREATE INDEX idx_indirect_clients_parent ON indirect_clients(parent_client_id);
CREATE INDEX idx_indirect_clients_parent_profile ON indirect_clients(parent_profile_id);

-- Related Persons table
CREATE TABLE indirect_client_persons (
    person_id UNIQUEIDENTIFIER PRIMARY KEY,       -- UUID
    indirect_client_id VARCHAR(50) NOT NULL,      -- URN reference to indirect_clients.client_id
    name NVARCHAR(255) NOT NULL,                  -- May contain Unicode
    role VARCHAR(20) NOT NULL,                    -- ADMIN or CONTACT
    email VARCHAR(255),                           -- ASCII only
    phone VARCHAR(20),                            -- Digits only (normalized)
    added_at DATETIME2 NOT NULL,

    CONSTRAINT FK_person_indirect_client FOREIGN KEY (indirect_client_id)
        REFERENCES indirect_clients(client_id) ON DELETE CASCADE
);

CREATE INDEX idx_indirect_client_persons_client ON indirect_client_persons(indirect_client_id);

-- Client Accounts
CREATE TABLE client_accounts (
    account_id VARCHAR(100) PRIMARY KEY,          -- URN string
    client_id VARCHAR(100),                       -- URN string
    indirect_client_id VARCHAR(50),               -- URN reference
    account_system VARCHAR(50) NOT NULL,          -- CDR, SRF, etc.
    account_type VARCHAR(50) NOT NULL,            -- DDA, PAP, etc.
    currency CHAR(3) NOT NULL,                    -- ISO 4217
    account_holder_name NVARCHAR(255),
    status VARCHAR(20) NOT NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    CONSTRAINT FK_client_accounts_client FOREIGN KEY (client_id)
        REFERENCES clients(client_id),
    CONSTRAINT FK_client_accounts_indirect_client FOREIGN KEY (indirect_client_id)
        REFERENCES indirect_clients(client_id)
);

CREATE INDEX idx_client_accounts_client ON client_accounts(client_id);
CREATE INDEX idx_client_accounts_system ON client_accounts(account_system);
CREATE INDEX idx_client_accounts_indirect_client ON client_accounts(indirect_client_id)
    WHERE indirect_client_id IS NOT NULL;

-- Profile Client Enrollments
CREATE TABLE profile_client_enrollments (
    id UNIQUEIDENTIFIER PRIMARY KEY,              -- UUID
    profile_id VARCHAR(200) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    is_primary BIT NOT NULL DEFAULT 0,
    account_enrollment_type VARCHAR(20) NOT NULL, -- ALL, SELECTED
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
    enrollment_id UNIQUEIDENTIFIER PRIMARY KEY,   -- UUID
    profile_id VARCHAR(200) NOT NULL,
    service_type VARCHAR(100) NOT NULL,           -- RECEIVABLES, etc.
    configuration NVARCHAR(MAX),                  -- JSON configuration
    status VARCHAR(20) NOT NULL,
    enrolled_at DATETIME2 NOT NULL,
    CONSTRAINT FK_service_enrollments_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id)
);

CREATE INDEX idx_service_enrollments_profile ON service_enrollments(profile_id);
CREATE INDEX idx_service_enrollments_type ON service_enrollments(service_type);

-- Account Enrollments
CREATE TABLE account_enrollments (
    enrollment_id UNIQUEIDENTIFIER PRIMARY KEY,   -- UUID
    profile_id VARCHAR(200) NOT NULL,
    service_enrollment_id UNIQUEIDENTIFIER,       -- UUID
    client_id VARCHAR(100) NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
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

-- =====================================================
-- USERS (Consolidated)
-- =====================================================

CREATE TABLE users (
    user_id UNIQUEIDENTIFIER PRIMARY KEY,         -- UUID
    login_id VARCHAR(50) NOT NULL UNIQUE,         -- Login ID
    email VARCHAR(255) NOT NULL,           -- ASCII only
    first_name NVARCHAR(100),                     -- May contain Unicode
    last_name NVARCHAR(100),                      -- May contain Unicode
    user_type VARCHAR(20) NOT NULL,               -- CLIENT_USER, etc.
    identity_provider VARCHAR(20) NOT NULL,       -- ANP, AUTH0
    identity_provider_user_id VARCHAR(255),       -- External provider ID
    profile_id VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,                  -- PENDING_CREATION, etc.
    email_verified BIT NOT NULL DEFAULT 0,
    password_set BIT NOT NULL DEFAULT 0,
    mfa_enrolled BIT NOT NULL DEFAULT 0,
    mfa_preference VARCHAR(20),                   -- Added
    allow_mfa_reenrollment BIT NOT NULL DEFAULT 0, -- Added
    mfa_reenrollment_requested_at DATETIME2,      -- Added
    mfa_reenrollment_requested_by NVARCHAR(255),  -- Added
    last_synced_at DATETIME2,
    last_logged_in_at DATETIME2,
    lock_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    locked_by NVARCHAR(255),
    locked_at DATETIME2,
    deactivation_reason NVARCHAR(500),
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255),
    updated_at DATETIME2 NOT NULL
);

CREATE INDEX idx_users_login_id ON users(login_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_profile ON users(profile_id);
CREATE UNIQUE INDEX idx_users_idp_user_id ON users(identity_provider_user_id)
    WHERE identity_provider_user_id IS NOT NULL;

-- User Roles Table
CREATE TABLE user_roles (
    user_id UNIQUEIDENTIFIER NOT NULL,
    role VARCHAR(20) NOT NULL,
    assigned_at DATETIME2 NOT NULL,
    assigned_by NVARCHAR(255) NOT NULL,

    PRIMARY KEY (user_id, role),
    CONSTRAINT FK_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT CHK_user_role CHECK (role IN (
        'SECURITY_ADMIN',
        'SERVICE_ADMIN',
        'READER',
        'CREATOR',
        'APPROVER'
    ))
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role);

-- =====================================================
-- PERMISSION POLICIES
-- =====================================================

CREATE TABLE permission_policies (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,          -- USER, GROUP, ROLE
    subject_identifier VARCHAR(255) NOT NULL,   -- UUID or role name
    action_pattern VARCHAR(500) NOT NULL,
    resource_pattern VARCHAR(1000) NOT NULL DEFAULT '*',
    effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW',
    description NVARCHAR(500),
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT FK_permission_policies_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id),
    CONSTRAINT CHK_permission_effect CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT CHK_permission_subject_type CHECK (subject_type IN ('USER', 'GROUP', 'ROLE'))
);

CREATE INDEX idx_permission_policies_profile ON permission_policies(profile_id);
CREATE INDEX idx_permission_policies_subject ON permission_policies(subject_type, subject_identifier);
CREATE INDEX idx_permission_policies_action ON permission_policies(action_pattern);

-- User Groups Table
CREATE TABLE user_groups (
    group_id UNIQUEIDENTIFIER PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(500),
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT FK_user_groups_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id),
    CONSTRAINT UQ_user_groups_name UNIQUE(profile_id, name)
);

CREATE INDEX idx_user_groups_profile ON user_groups(profile_id);

-- User Group Members Table
CREATE TABLE user_group_members (
    group_id UNIQUEIDENTIFIER NOT NULL,
    user_id UNIQUEIDENTIFIER NOT NULL,
    added_at DATETIME2 NOT NULL,
    added_by NVARCHAR(255) NOT NULL,

    PRIMARY KEY (group_id, user_id),
    CONSTRAINT FK_user_group_members_group FOREIGN KEY (group_id)
        REFERENCES user_groups(group_id) ON DELETE CASCADE,
    CONSTRAINT FK_user_group_members_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_group_members_group ON user_group_members(group_id);
CREATE INDEX idx_user_group_members_user ON user_group_members(user_id);

-- =====================================================
-- ACCOUNT GROUPS (Consolidated)
-- =====================================================

CREATE TABLE account_groups (
    group_id UNIQUEIDENTIFIER PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(500),
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT FK_account_groups_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id),
    CONSTRAINT UQ_account_groups_name UNIQUE(profile_id, name)
);

CREATE INDEX idx_account_groups_profile ON account_groups(profile_id);

CREATE TABLE account_group_members (
    group_id UNIQUEIDENTIFIER NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    added_at DATETIME2 NOT NULL,

    PRIMARY KEY (group_id, account_id),
    CONSTRAINT FK_account_group_members_group FOREIGN KEY (group_id)
        REFERENCES account_groups(group_id) ON DELETE CASCADE
);

CREATE INDEX idx_account_group_members_group ON account_group_members(group_id);
CREATE INDEX idx_account_group_members_account ON account_group_members(account_id);

-- =====================================================
-- BATCH PROCESSING TABLES
-- =====================================================

CREATE TABLE batches (
    batch_id UNIQUEIDENTIFIER PRIMARY KEY,
    batch_type VARCHAR(50) NOT NULL,
    source_profile_id VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_items INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    started_at DATETIME2,
    completed_at DATETIME2,

    CONSTRAINT CHK_batch_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'
    )),
    CONSTRAINT CHK_batch_type CHECK (batch_type IN (
        'PAYOR_ENROLMENT'
    )),
    CONSTRAINT FK_batches_profile FOREIGN KEY (source_profile_id)
        REFERENCES profiles(profile_id)
);

CREATE INDEX idx_batches_profile ON batches(source_profile_id);
CREATE INDEX idx_batches_status ON batches(status);
CREATE INDEX idx_batches_created ON batches(created_at DESC);

CREATE TABLE batch_items (
    batch_item_id UNIQUEIDENTIFIER PRIMARY KEY,
    batch_id UNIQUEIDENTIFIER NOT NULL,
    sequence_number INT NOT NULL,
    input_data NVARCHAR(MAX) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_data NVARCHAR(MAX),
    error_message NVARCHAR(2000),
    processed_at DATETIME2,

    CONSTRAINT CHK_batch_item_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'SUCCESS', 'FAILED'
    )),
    CONSTRAINT FK_batch_items_batch FOREIGN KEY (batch_id)
        REFERENCES batches(batch_id) ON DELETE CASCADE,
    CONSTRAINT UQ_batch_item_sequence UNIQUE (batch_id, sequence_number)
);

CREATE INDEX idx_batch_items_batch ON batch_items(batch_id);
CREATE INDEX idx_batch_items_status ON batch_items(status);