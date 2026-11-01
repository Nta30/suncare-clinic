
-- SUNCARE CLINIC
-- Migration V2: Identity & Organization Relations
-- Requires: V1
-- Database: SuncareClinic
-- SQL Server 2025
--
-- CHUA THUC THI FILE NAY.
-- Khong chay truc tiep trong SSMS.

-- ============================================
-- 1. ACCOUNT ROLE
-- ============================================

CREATE TABLE dbo.AccountRole (
                                 account_id BIGINT NOT NULL,
                                 role_id BIGINT NOT NULL,
                                 granted_by BIGINT NOT NULL,
                                 granted_at DATETIME2(3) NOT NULL,

                                 created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_AccountRole_created_at
        DEFAULT (SYSUTCDATETIME()),

                                 CONSTRAINT PK_AccountRole
                                     PRIMARY KEY (account_id, role_id),

                                 CONSTRAINT FK_AccountRole_Account
                                     FOREIGN KEY (account_id)
                                         REFERENCES dbo.[Account](id),

                                 CONSTRAINT FK_AccountRole_Role
                                     FOREIGN KEY (role_id)
                                         REFERENCES dbo.[Role](id),

                                 CONSTRAINT FK_AccountRole_GrantedBy
                                     FOREIGN KEY (granted_by)
                                         REFERENCES dbo.[Account](id)
);

-- ============================================
-- 2. ROLE PERMISSION
-- ============================================

CREATE TABLE dbo.RolePermission (
                                    role_id BIGINT NOT NULL,
                                    permission_id BIGINT NOT NULL,

                                    created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_RolePermission_created_at
        DEFAULT (SYSUTCDATETIME()),

                                    CONSTRAINT PK_RolePermission
                                        PRIMARY KEY (role_id, permission_id),

                                    CONSTRAINT FK_RolePermission_Role
                                        FOREIGN KEY (role_id)
                                            REFERENCES dbo.[Role](id),

                                    CONSTRAINT FK_RolePermission_Permission
                                        FOREIGN KEY (permission_id)
                                            REFERENCES dbo.[Permission](id)
);

-- ============================================
-- 3. DEPARTMENT SPECIALTY
-- ============================================

CREATE TABLE dbo.DepartmentSpecialty (
                                         department_id BIGINT NOT NULL,
                                         specialty_id BIGINT NOT NULL,

                                         created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_DepartmentSpecialty_created_at
        DEFAULT (SYSUTCDATETIME()),

                                         CONSTRAINT PK_DepartmentSpecialty
                                             PRIMARY KEY (department_id, specialty_id),

                                         CONSTRAINT FK_DepartmentSpecialty_Department
                                             FOREIGN KEY (department_id)
                                                 REFERENCES dbo.Department(id),

                                         CONSTRAINT FK_DepartmentSpecialty_Specialty
                                             FOREIGN KEY (specialty_id)
                                                 REFERENCES dbo.Specialty(id)
);

-- ============================================
-- 4. STAFF DEPARTMENT
-- ============================================

CREATE TABLE dbo.StaffDepartment (
                                     id BIGINT IDENTITY(1,1) NOT NULL,
                                     staff_id BIGINT NOT NULL,
                                     department_id BIGINT NOT NULL,
                                     valid_from DATETIME2(3) NOT NULL,
                                     valid_to DATETIME2(3) NULL,

                                     is_primary BIT NOT NULL
                                         CONSTRAINT DF_StaffDepartment_is_primary
                                         DEFAULT (1),

                                     created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_StaffDepartment_created_at
        DEFAULT (SYSUTCDATETIME()),

                                     updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_StaffDepartment_updated_at
        DEFAULT (SYSUTCDATETIME()),

                                     version ROWVERSION NOT NULL,

                                     CONSTRAINT PK_StaffDepartment
                                         PRIMARY KEY (id),

                                     CONSTRAINT FK_StaffDepartment_Staff
                                         FOREIGN KEY (staff_id)
                                             REFERENCES dbo.Staff(id),

                                     CONSTRAINT FK_StaffDepartment_Department
                                         FOREIGN KEY (department_id)
                                             REFERENCES dbo.Department(id),

                                     CONSTRAINT CK_StaffDepartment_valid_period
                                         CHECK (
                                             valid_to IS NULL
                                                 OR valid_to > valid_from
                                             )
);

-- ============================================
-- 5. STAFF SPECIALTY
-- ============================================

CREATE TABLE dbo.StaffSpecialty (
                                    staff_id BIGINT NOT NULL,
                                    specialty_id BIGINT NOT NULL,
                                    is_active BIT NOT NULL,

                                    created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_StaffSpecialty_created_at
        DEFAULT (SYSUTCDATETIME()),

                                    updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_StaffSpecialty_updated_at
        DEFAULT (SYSUTCDATETIME()),

                                    version ROWVERSION NOT NULL,

                                    CONSTRAINT PK_StaffSpecialty
                                        PRIMARY KEY (staff_id, specialty_id),

                                    CONSTRAINT FK_StaffSpecialty_Staff
                                        FOREIGN KEY (staff_id)
                                            REFERENCES dbo.Staff(id),

                                    CONSTRAINT FK_StaffSpecialty_Specialty
                                        FOREIGN KEY (specialty_id)
                                            REFERENCES dbo.Specialty(id)
);

-- ============================================
-- 6. DEPARTMENT HEAD ASSIGNMENT
-- ============================================

CREATE TABLE dbo.DepartmentHeadAssignment (
                                              id BIGINT IDENTITY(1,1) NOT NULL,
                                              staff_id BIGINT NOT NULL,
                                              department_id BIGINT NOT NULL,
                                              valid_from DATETIME2(3) NOT NULL,
                                              valid_to DATETIME2(3) NULL,
                                              assigned_by BIGINT NOT NULL,

                                              created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_DepartmentHead_created_at
        DEFAULT (SYSUTCDATETIME()),

                                              updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_DepartmentHead_updated_at
        DEFAULT (SYSUTCDATETIME()),

                                              version ROWVERSION NOT NULL,

                                              CONSTRAINT PK_DepartmentHeadAssignment
                                                  PRIMARY KEY (id),

                                              CONSTRAINT FK_DepartmentHead_Staff
                                                  FOREIGN KEY (staff_id)
                                                      REFERENCES dbo.Staff(id),

                                              CONSTRAINT FK_DepartmentHead_Department
                                                  FOREIGN KEY (department_id)
                                                      REFERENCES dbo.Department(id),

                                              CONSTRAINT FK_DepartmentHead_AssignedBy
                                                  FOREIGN KEY (assigned_by)
                                                      REFERENCES dbo.[Account](id),

                                              CONSTRAINT CK_DepartmentHead_valid_period
                                                  CHECK (
                                                      valid_to IS NULL
                                                          OR valid_to > valid_from
                                                      )
);

-- ============================================
-- 7. ROOM
-- ============================================

CREATE TABLE dbo.Room (
                          id BIGINT IDENTITY(1,1) NOT NULL,
                          code VARCHAR(40) NOT NULL,
                          department_id BIGINT NOT NULL,
                          name NVARCHAR(150) NOT NULL,
                          room_type VARCHAR(16) NOT NULL,
                          is_active BIT NOT NULL,

                          created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Room_created_at
        DEFAULT (SYSUTCDATETIME()),

                          updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Room_updated_at
        DEFAULT (SYSUTCDATETIME()),

                          version ROWVERSION NOT NULL,

                          CONSTRAINT PK_Room
                              PRIMARY KEY (id),

                          CONSTRAINT UQ_Room_code
                              UNIQUE (code),

                          CONSTRAINT FK_Room_Department
                              FOREIGN KEY (department_id)
                                  REFERENCES dbo.Department(id),

                          CONSTRAINT CK_Room_type
                              CHECK (
                                  room_type IN (
                                                'CONSULTATION',
                                                'LAB'
                                      )
                                  )
);

-- END OF MIGRATION V2
