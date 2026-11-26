CREATE TABLE dbo.DiagnosisConcept (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_DiagnosisConcept PRIMARY KEY,
    code_system VARCHAR(30) NOT NULL,
    code_version VARCHAR(30) NOT NULL,
    code VARCHAR(30) NOT NULL,
    name NVARCHAR(1000) NOT NULL,
    english_name NVARCHAR(1000) NULL,
    is_active BIT NOT NULL,
    is_selectable BIT NOT NULL,
    primary_allowed BIT NOT NULL,
    source_page INT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_DiagnosisConcept_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_DiagnosisConcept_code UNIQUE (code_system, code_version, code)
);
CREATE INDEX IX_DiagnosisConcept_lookup ON dbo.DiagnosisConcept(is_active, is_selectable, code);
ALTER TABLE dbo.Diagnosis ADD diagnosis_concept_id BIGINT NULL, code_version VARCHAR(30) NULL,
    diagnosis_name_snapshot NVARCHAR(1000) NULL;
GO
ALTER TABLE dbo.Diagnosis ADD CONSTRAINT FK_Diagnosis_concept
    FOREIGN KEY (diagnosis_concept_id) REFERENCES dbo.DiagnosisConcept(id);
-- Existing codes remain legacy values. Never infer an edition or rewrite issued clinical history.
