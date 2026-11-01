
-- SUNCARE CLINIC
-- Migration V1: Identity and Organization Core
-- Database: SuncareClinic
-- SQL Server 2025
--
-- CHUA THUC THI MIGRATION NAY.
-- Khong chay truc tiep trong SSMS.
-- Khong tao database moi.

-- ============================================
-- 1. PATIENT
-- ============================================

CREATE TABLE dbo.Patient (
                             id BIGINT IDENTITY(1,1) NOT NULL,
                             code VARCHAR(40) NOT NULL,
                             full_name NVARCHAR(150) NOT NULL,
                             avatar_url NVARCHAR(2048) NULL,
                             avatar_public_id NVARCHAR(255) NULL,
                             birth_date DATE NULL,
                             sex VARCHAR(16) NOT NULL,
                             phone VARCHAR(20) NULL,
                             email NVARCHAR(254) NULL,
                             address NVARCHAR(500) NULL,
                             identity_number VARCHAR(30) NULL,
                             emergency_name NVARCHAR(150) NULL,
                             emergency_phone VARCHAR(20) NULL,
                             is_active BIT NOT NULL,

                             created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Patient_created_at
        DEFAULT (SYSUTCDATETIME()),

                             updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Patient_updated_at
        DEFAULT (SYSUTCDATETIME()),

                             version ROWVERSION NOT NULL,

                             CONSTRAINT PK_Patient
                                 PRIMARY KEY (id),

                             CONSTRAINT UQ_Patient_code
                                 UNIQUE (code),

                             CONSTRAINT CK_Patient_sex
                                 CHECK (
                                     sex IN (
                                             'UNKNOWN',
                                             'MALE',
                                             'FEMALE',
                                             'OTHER'
                                         )
                                     )
);

-- ============================================
-- 2. STAFF
-- ============================================

CREATE TABLE dbo.Staff (
                           id BIGINT IDENTITY(1,1) NOT NULL,
                           code VARCHAR(40) NOT NULL,
                           full_name NVARCHAR(150) NOT NULL,
                           avatar_url NVARCHAR(2048) NULL,
                           avatar_public_id NVARCHAR(255) NULL,
                           birth_date DATE NULL,
                           sex VARCHAR(16) NOT NULL,
                           phone VARCHAR(20) NULL,
                           email NVARCHAR(254) NULL,
                           address NVARCHAR(500) NULL,
                           professional_license NVARCHAR(100) NULL,
                           qualification NVARCHAR(150) NULL,
                           is_active BIT NOT NULL,

                           created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Staff_created_at
        DEFAULT (SYSUTCDATETIME()),

                           updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Staff_updated_at
        DEFAULT (SYSUTCDATETIME()),

                           version ROWVERSION NOT NULL,

                           CONSTRAINT PK_Staff
                               PRIMARY KEY (id),

                           CONSTRAINT UQ_Staff_code
                               UNIQUE (code)
);

-- ============================================
-- 3. DEPARTMENT
-- ============================================

CREATE TABLE dbo.Department (
                                id BIGINT IDENTITY(1,1) NOT NULL,
                                code VARCHAR(40) NOT NULL,
                                name NVARCHAR(150) NOT NULL,
                                department_type VARCHAR(16) NOT NULL,
                                is_active BIT NOT NULL,

                                created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Department_created_at
        DEFAULT (SYSUTCDATETIME()),

                                updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Department_updated_at
        DEFAULT (SYSUTCDATETIME()),

                                version ROWVERSION NOT NULL,

                                CONSTRAINT PK_Department
                                    PRIMARY KEY (id),

                                CONSTRAINT UQ_Department_code
                                    UNIQUE (code),

                                CONSTRAINT CK_Department_type
                                    CHECK (
                                        department_type IN (
                                                            'CLINICAL',
                                                            'LAB'
                                            )
                                        )
);

-- ============================================
-- 4. SPECIALTY
-- ============================================

CREATE TABLE dbo.Specialty (
                               id BIGINT IDENTITY(1,1) NOT NULL,
                               code VARCHAR(40) NOT NULL,
                               name NVARCHAR(150) NOT NULL,
                               is_active BIT NOT NULL,

                               created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Specialty_created_at
        DEFAULT (SYSUTCDATETIME()),

                               updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_Specialty_updated_at
        DEFAULT (SYSUTCDATETIME()),

                               version ROWVERSION NOT NULL,

                               CONSTRAINT PK_Specialty
                                   PRIMARY KEY (id),

                               CONSTRAINT UQ_Specialty_code
                                   UNIQUE (code)
);

-- ============================================
-- 5. ROLE
-- ============================================

CREATE TABLE dbo.[Role] (
                            id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    account_type VARCHAR(16) NOT NULL,

    created_at DATETIME2(3) NOT NULL
    CONSTRAINT DF_Role_created_at
    DEFAULT (SYSUTCDATETIME()),

    updated_at DATETIME2(3) NOT NULL
    CONSTRAINT DF_Role_updated_at
    DEFAULT (SYSUTCDATETIME()),

    version ROWVERSION NOT NULL,

    CONSTRAINT PK_Role
    PRIMARY KEY (id),

    CONSTRAINT UQ_Role_code
    UNIQUE (code),

    CONSTRAINT CK_Role_code_type
    CHECK (
(
              code = 'PATIENT'
              AND account_type = 'PATIENT'
)
    OR
(
    code IN (
    'DOCTOR',
    'LAB_STAFF',
    'DEPARTMENT_HEAD',
    'CASHIER',
    'SYSTEM_ADMIN'
            )
    AND account_type = 'STAFF'
    )
    )
    );

-- ============================================
-- 6. PERMISSION
-- ============================================

CREATE TABLE dbo.[Permission] (
                                  id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    description NVARCHAR(300) NOT NULL,

    created_at DATETIME2(3) NOT NULL
    CONSTRAINT DF_Permission_created_at
    DEFAULT (SYSUTCDATETIME()),

    updated_at DATETIME2(3) NOT NULL
    CONSTRAINT DF_Permission_updated_at
    DEFAULT (SYSUTCDATETIME()),

    version ROWVERSION NOT NULL,

    CONSTRAINT PK_Permission
    PRIMARY KEY (id),

    CONSTRAINT UQ_Permission_code
    UNIQUE (code)
    );

-- ============================================
-- 7. ACCOUNT
-- ============================================

CREATE TABLE dbo.[Account] (
                               id BIGINT IDENTITY(1,1) NOT NULL,
    login_name NVARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    patient_id BIGINT NULL,
    staff_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    last_login_at DATETIME2(3) NULL,

    created_at DATETIME2(3) NOT NULL
    CONSTRAINT DF_Account_created_at
    DEFAULT (SYSUTCDATETIME()),

    updated_at DATETIME2(3) NOT NULL
    CONSTRAINT DF_Account_updated_at
    DEFAULT (SYSUTCDATETIME()),

    version ROWVERSION NOT NULL,

    CONSTRAINT PK_Account
    PRIMARY KEY (id),

    CONSTRAINT UQ_Account_login_name
    UNIQUE (login_name),

    CONSTRAINT FK_Account_Patient
    FOREIGN KEY (patient_id)
    REFERENCES dbo.Patient(id),

    CONSTRAINT FK_Account_Staff
    FOREIGN KEY (staff_id)
    REFERENCES dbo.Staff(id),

    CONSTRAINT CK_Account_status
    CHECK (
              status IN (
              'ACTIVE',
              'LOCKED'
                        )
    ),

    CONSTRAINT CK_Account_profile
    CHECK (
(
              account_type = 'PATIENT'
              AND patient_id IS NOT NULL
              AND staff_id IS NULL
)
    OR
(
    account_type = 'STAFF'
    AND staff_id IS NOT NULL
    AND patient_id IS NULL
)
    )
    );

-- Moi Patient chi gan toi da mot Account.
CREATE UNIQUE INDEX UX_Account_patient_id
    ON dbo.[Account](patient_id)
    WHERE patient_id IS NOT NULL;

-- Moi Staff chi gan toi da mot Account.
CREATE UNIQUE INDEX UX_Account_staff_id
    ON dbo.[Account](staff_id)
    WHERE staff_id IS NOT NULL;

-- END OF MIGRATION V1
