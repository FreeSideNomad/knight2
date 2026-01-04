-- User Groups table
CREATE TABLE user_groups (
    group_id UNIQUEIDENTIFIER PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(500),
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL,
    CONSTRAINT UQ_user_groups_name UNIQUE(profile_id, name)
);

-- User Group Members table (junction table for group membership)
CREATE TABLE user_group_members (
    group_id UNIQUEIDENTIFIER NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    added_at DATETIME2 NOT NULL,
    added_by NVARCHAR(255) NOT NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT FK_user_group_members_group FOREIGN KEY (group_id)
        REFERENCES user_groups(group_id) ON DELETE CASCADE
);

-- Index for efficient lookup by profile
CREATE INDEX IX_user_groups_profile ON user_groups(profile_id);

-- Index for efficient lookup by user
CREATE INDEX IX_user_group_members_user ON user_group_members(user_id);
