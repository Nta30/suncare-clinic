
-- SUNCARE CLINIC
-- Migration V3: Catalog and Work Scheduling
-- Database: SuncareClinic
-- SQL Server 2025
-- Requires: V1 and V2
--
-- CHUA THUC THI MIGRATION.
-- Khong chay truc tiep trong SSMS.
-- Khong tao database moi.

-- ============================================
-- 1. SERVICE
-- ============================================

CREATE TABLE dbo.[Service] (
                               id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name NVARCHAR(200) NOT NULL,
    service_type VARCHAR(16) NOT NULL,
    result_type VARCHAR(16) NULL,
    specialty_id BIGINT NULL,
    is_active BIT NOT NULL,

    created_at DATETIME2(3) NOT NULL
    CONSTRAINT DF_Service_created_at
    DEFAULT (SYSUTCDATETIME()),

    updated_at DATETIME2(3) NOT NULL
    CONSTRAINT DF_Service_updated_at
    DEFAULT (SYSUTCDATETIME()),

    version ROWVERSION NOT NULL,

    CONSTRAINT PK_Service
    PRIMARY KEY (id),

    CONSTRAINT UQ_Service_code
    UNIQUE (code),

    CONSTRAINT FK_Service_Specialty
    FOREIGN KEY (specialty_id)
    REFERENCES dbo.Specialty(id),

    CONSTRAINT CK_Service_type_result
    CHECK (
(
              service_type = 'EXAMINATION'
              AND result_type IS NULL
)
    OR
(
    service_type = 'CLS'
    AND (
    result_type IS NULL
    OR result_type IN ('LAB', 'IMAGING')
    )
    )
    )
    );

-- ============================================
-- 2. ROOM SERVICE
-- ============================================

CREATE TABLE dbo.RoomService (
                                 room_id BIGINT NOT NULL,
                                 service_id BIGINT NOT NULL,
                                 is_active BIT NOT NULL,

                                 created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_RoomService_created_at
        DEFAULT (SYSUTCDATETIME()),

                                 updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_RoomService_updated_at
        DEFAULT (SYSUTCDATETIME()),

                                 version ROWVERSION NOT NULL,

                                 CONSTRAINT PK_RoomService
                                     PRIMARY KEY (room_id, service_id),

                                 CONSTRAINT FK_RoomService_Room
                                     FOREIGN KEY (room_id)
                                         REFERENCES dbo.Room(id),

                                 CONSTRAINT FK_RoomService_Service
                                     FOREIGN KEY (service_id)
                                         REFERENCES dbo.[Service](id)
);

-- ============================================
-- 3. SERVICE PRICE
-- ============================================

CREATE TABLE dbo.ServicePrice (
                                  id BIGINT IDENTITY(1,1) NOT NULL,
                                  service_id BIGINT NOT NULL,
                                  doctor_id BIGINT NULL,
                                  visit_mode VARCHAR(16) NULL,
                                  amount DECIMAL(19,0) NOT NULL,
                                  valid_from DATETIME2(3) NOT NULL,
                                  valid_to DATETIME2(3) NULL,
                                  created_by BIGINT NOT NULL,

                                  created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_ServicePrice_created_at
        DEFAULT (SYSUTCDATETIME()),

                                  updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_ServicePrice_updated_at
        DEFAULT (SYSUTCDATETIME()),

                                  version ROWVERSION NOT NULL,

                                  CONSTRAINT PK_ServicePrice
                                      PRIMARY KEY (id),

                                  CONSTRAINT FK_ServicePrice_Service
                                      FOREIGN KEY (service_id)
                                          REFERENCES dbo.[Service](id),

                                  CONSTRAINT FK_ServicePrice_Doctor
                                      FOREIGN KEY (doctor_id)
                                          REFERENCES dbo.Staff(id),

                                  CONSTRAINT FK_ServicePrice_CreatedBy
                                      FOREIGN KEY (created_by)
                                          REFERENCES dbo.[Account](id),

                                  CONSTRAINT CK_ServicePrice_amount
                                      CHECK (amount > 0),

                                  CONSTRAINT CK_ServicePrice_valid_period
                                      CHECK (
                                          valid_to IS NULL
                                              OR valid_to > valid_from
                                          ),

                                  CONSTRAINT CK_ServicePrice_visit_mode
                                      CHECK (
                                          visit_mode IS NULL
                                              OR visit_mode IN ('ONSITE', 'ONLINE')
                                          ),

                                  CONSTRAINT CK_ServicePrice_doctor_mode
                                      CHECK (
                                          (
                                              doctor_id IS NULL
                                                  AND visit_mode IS NULL
                                              )
                                              OR
                                          (
                                              doctor_id IS NOT NULL
                                                  AND visit_mode IS NOT NULL
                                              )
                                          )
);

-- ============================================
-- 4. SHIFT DEFINITION
-- ============================================

CREATE TABLE dbo.ShiftDefinition (
                                     id BIGINT IDENTITY(1,1) NOT NULL,
                                     code VARCHAR(40) NOT NULL,
                                     name NVARCHAR(100) NOT NULL,
                                     local_start TIME(0) NOT NULL,
                                     local_end TIME(0) NOT NULL,
                                     is_active BIT NOT NULL,

                                     created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_ShiftDefinition_created_at
        DEFAULT (SYSUTCDATETIME()),

                                     updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_ShiftDefinition_updated_at
        DEFAULT (SYSUTCDATETIME()),

                                     version ROWVERSION NOT NULL,

                                     CONSTRAINT PK_ShiftDefinition
                                         PRIMARY KEY (id),

                                     CONSTRAINT UQ_ShiftDefinition_code
                                         UNIQUE (code),

                                     CONSTRAINT CK_ShiftDefinition_code
                                         CHECK (
                                             code IN ('MORNING', 'AFTERNOON')
                                             ),

                                     CONSTRAINT CK_ShiftDefinition_time
                                         CHECK (local_end > local_start)
);

-- ============================================
-- 5. WORK SCHEDULE
-- ============================================

CREATE TABLE dbo.WorkSchedule (
                                  id BIGINT IDENTITY(1,1) NOT NULL,
                                  department_id BIGINT NOT NULL,
                                  period_start DATE NOT NULL,
                                  period_end DATE NOT NULL,
                                  status VARCHAR(32) NOT NULL,
                                  published_at DATETIME2(3) NULL,
                                  published_by BIGINT NULL,

                                  created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_WorkSchedule_created_at
        DEFAULT (SYSUTCDATETIME()),

                                  updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_WorkSchedule_updated_at
        DEFAULT (SYSUTCDATETIME()),

                                  version ROWVERSION NOT NULL,

                                  CONSTRAINT PK_WorkSchedule
                                      PRIMARY KEY (id),

                                  CONSTRAINT FK_WorkSchedule_Department
                                      FOREIGN KEY (department_id)
                                          REFERENCES dbo.Department(id),

                                  CONSTRAINT FK_WorkSchedule_PublishedBy
                                      FOREIGN KEY (published_by)
                                          REFERENCES dbo.[Account](id),

                                  CONSTRAINT CK_WorkSchedule_period
                                      CHECK (
                                          period_end >= period_start
                                          ),

                                  CONSTRAINT CK_WorkSchedule_status
                                      CHECK (
                                          status IN ('DRAFT', 'PUBLISHED')
                                          )
);

-- ============================================
-- 6. WORK ASSIGNMENT
-- ============================================

CREATE TABLE dbo.WorkAssignment (
                                    id BIGINT IDENTITY(1,1) NOT NULL,
                                    schedule_id BIGINT NOT NULL,
                                    staff_id BIGINT NOT NULL,
                                    room_id BIGINT NULL,
                                    specialty_id BIGINT NULL,
                                    work_date DATE NOT NULL,
                                    shift_id BIGINT NULL,
                                    mode VARCHAR(16) NOT NULL,
                                    starts_at DATETIME2(3) NOT NULL,
                                    ends_at DATETIME2(3) NOT NULL,
                                    capacity INT NULL,

                                    created_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_WorkAssignment_created_at
        DEFAULT (SYSUTCDATETIME()),

                                    updated_at DATETIME2(3) NOT NULL
        CONSTRAINT DF_WorkAssignment_updated_at
        DEFAULT (SYSUTCDATETIME()),

                                    version ROWVERSION NOT NULL,

                                    CONSTRAINT PK_WorkAssignment
                                        PRIMARY KEY (id),

                                    CONSTRAINT FK_WorkAssignment_Schedule
                                        FOREIGN KEY (schedule_id)
                                            REFERENCES dbo.WorkSchedule(id),

                                    CONSTRAINT FK_WorkAssignment_Staff
                                        FOREIGN KEY (staff_id)
                                            REFERENCES dbo.Staff(id),

                                    CONSTRAINT FK_WorkAssignment_Room
                                        FOREIGN KEY (room_id)
                                            REFERENCES dbo.Room(id),

                                    CONSTRAINT FK_WorkAssignment_Specialty
                                        FOREIGN KEY (specialty_id)
                                            REFERENCES dbo.Specialty(id),

                                    CONSTRAINT FK_WorkAssignment_Shift
                                        FOREIGN KEY (shift_id)
                                            REFERENCES dbo.ShiftDefinition(id),

                                    CONSTRAINT CK_WorkAssignment_time
                                        CHECK (
                                            ends_at > starts_at
                                            ),

                                    CONSTRAINT CK_WorkAssignment_mode
                                        CHECK (
                                            (
                                                mode = 'ONSITE'
                                                    AND room_id IS NOT NULL
                                                    AND shift_id IS NOT NULL
                                                    AND specialty_id IS NOT NULL
                                                    AND capacity IS NOT NULL
                                                    AND capacity > 0
                                                )
                                                OR
                                            (
                                                mode = 'ONLINE'
                                                    AND specialty_id IS NOT NULL
                                                    AND room_id IS NULL
                                                    AND shift_id IS NULL
                                                    AND capacity IS NULL
                                                )
                                                OR
                                            (
                                                mode = 'LAB'
                                                    AND room_id IS NOT NULL
                                                    AND capacity IS NULL
                                                )
                                            )
);

-- END OF MIGRATION V3
