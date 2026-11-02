ALTER TABLE dbo.DepartmentSpecialty
ADD is_active BIT NOT NULL CONSTRAINT DF_DepartmentSpecialty_is_active DEFAULT (1),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_DepartmentSpecialty_updated_at DEFAULT (SYSUTCDATETIME()),
    version ROWVERSION;
