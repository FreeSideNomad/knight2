-- Create knight database if it doesn't exist
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'knight')
BEGIN
    CREATE DATABASE knight;
END
GO

USE knight;
GO
