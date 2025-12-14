-- Servicing Profiles table
CREATE TABLE servicing_profiles (
    profile_id NVARCHAR(200) NOT NULL PRIMARY KEY,
    client_id NVARCHAR(100) NOT NULL,
    status NVARCHAR(20) NOT NULL,
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL
);

-- Service Enrollments table
CREATE TABLE service_enrollments (
    enrollment_id NVARCHAR(100) NOT NULL PRIMARY KEY,
    profile_id NVARCHAR(200) NOT NULL,
    service_type NVARCHAR(100) NOT NULL,
    configuration NVARCHAR(MAX),
    status NVARCHAR(20) NOT NULL,
    enrolled_at DATETIME2 NOT NULL,
    CONSTRAINT FK_service_enrollments_profile FOREIGN KEY (profile_id)
        REFERENCES servicing_profiles(profile_id)
);

-- Account Enrollments table
CREATE TABLE account_enrollments (
    enrollment_id NVARCHAR(100) NOT NULL PRIMARY KEY,
    profile_id NVARCHAR(200) NOT NULL,
    service_enrollment_id NVARCHAR(100) NOT NULL,
    account_id NVARCHAR(100) NOT NULL,
    status NVARCHAR(20) NOT NULL,
    enrolled_at DATETIME2 NOT NULL,
    CONSTRAINT FK_account_enrollments_profile FOREIGN KEY (profile_id)
        REFERENCES servicing_profiles(profile_id),
    CONSTRAINT FK_account_enrollments_service FOREIGN KEY (service_enrollment_id)
        REFERENCES service_enrollments(enrollment_id)
);

-- Indexes
CREATE INDEX IX_servicing_profiles_client_id ON servicing_profiles(client_id);
CREATE INDEX IX_service_enrollments_profile_id ON service_enrollments(profile_id);
CREATE INDEX IX_account_enrollments_profile_id ON account_enrollments(profile_id);
CREATE INDEX IX_account_enrollments_service_id ON account_enrollments(service_enrollment_id);
