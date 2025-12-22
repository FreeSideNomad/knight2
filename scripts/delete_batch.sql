-- =====================================================
-- DELETE BATCH IMPORTED DATA
-- Deletes all data created by batch imports in correct order
-- due to referential integrity constraints
-- =====================================================

-- Use knight database
USE knight;
GO

SET QUOTED_IDENTIFIER ON;
GO

BEGIN TRANSACTION;

-- Step 1: Delete user_group_members (references users)
DELETE ugm
FROM user_group_members ugm
INNER JOIN users u ON ugm.user_id = u.user_id
INNER JOIN profiles p ON u.profile_id = p.profile_id
WHERE p.profile_type = 'INDIRECT';

PRINT 'Deleted user_group_members for INDIRECT profiles';

-- Step 2: Delete user_roles (references users, CASCADE should handle but explicit is safer)
DELETE ur
FROM user_roles ur
INNER JOIN users u ON ur.user_id = u.user_id
INNER JOIN profiles p ON u.profile_id = p.profile_id
WHERE p.profile_type = 'INDIRECT';

PRINT 'Deleted user_roles for INDIRECT profiles';

-- Step 3: Delete users linked to INDIRECT profiles
DELETE u
FROM users u
INNER JOIN profiles p ON u.profile_id = p.profile_id
WHERE p.profile_type = 'INDIRECT';

PRINT 'Deleted users for INDIRECT profiles';

-- Step 4: Delete permission_policies for INDIRECT profiles
DELETE pp
FROM permission_policies pp
INNER JOIN profiles p ON pp.profile_id = p.profile_id
WHERE p.profile_type = 'INDIRECT';

PRINT 'Deleted permission_policies for INDIRECT profiles';

-- Step 5: Delete user_groups for INDIRECT profiles
DELETE ug
FROM user_groups ug
INNER JOIN profiles p ON ug.profile_id = p.profile_id
WHERE p.profile_type = 'INDIRECT';

PRINT 'Deleted user_groups for INDIRECT profiles';

-- Step 6: Delete account_enrollments for INDIRECT profiles
DELETE ae
FROM account_enrollments ae
INNER JOIN profiles p ON ae.profile_id = p.profile_id
WHERE p.profile_type = 'INDIRECT';

PRINT 'Deleted account_enrollments for INDIRECT profiles';

-- Step 7: Delete service_enrollments for INDIRECT profiles
DELETE se
FROM service_enrollments se
INNER JOIN profiles p ON se.profile_id = p.profile_id
WHERE p.profile_type = 'INDIRECT';

PRINT 'Deleted service_enrollments for INDIRECT profiles';

-- Step 8: Delete profile_client_enrollments for INDIRECT profiles
DELETE pce
FROM profile_client_enrollments pce
INNER JOIN profiles p ON pce.profile_id = p.profile_id
WHERE p.profile_type = 'INDIRECT';

PRINT 'Deleted profile_client_enrollments for INDIRECT profiles';

-- Step 9: Delete client_accounts linked to indirect_clients (OFI accounts)
DELETE ca
FROM client_accounts ca
WHERE ca.indirect_client_id IS NOT NULL;

PRINT 'Deleted client_accounts for indirect_clients';

-- Step 10: Delete indirect_client_persons (CASCADE should handle, but explicit)
DELETE icp
FROM indirect_client_persons icp;

PRINT 'Deleted indirect_client_persons';

-- Step 11: Delete indirect_clients
DELETE FROM indirect_clients;

PRINT 'Deleted indirect_clients';

-- Step 12: Delete INDIRECT profiles
DELETE FROM profiles
WHERE profile_type = 'INDIRECT';

PRINT 'Deleted INDIRECT profiles';

-- Step 13: Delete batch_items (CASCADE from batches, but explicit)
DELETE FROM batch_items;

PRINT 'Deleted batch_items';

-- Step 14: Delete batches
DELETE FROM batches;

PRINT 'Deleted batches';

COMMIT TRANSACTION;

PRINT '';
PRINT '=====================================================';
PRINT 'Batch imported data deleted successfully';
PRINT '=====================================================';
GO
