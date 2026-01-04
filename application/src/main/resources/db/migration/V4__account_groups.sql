-- =====================================================
-- ACCOUNT GROUPS - For permission scoping
-- =====================================================

-- Account Groups table
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

-- Account Group Members table (accounts in a group)
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
