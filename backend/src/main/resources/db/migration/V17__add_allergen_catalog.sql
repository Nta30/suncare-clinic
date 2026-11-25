CREATE TABLE dbo.Allergen (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Allergen PRIMARY KEY,
    code VARCHAR(40) NOT NULL CONSTRAINT UQ_Allergen_code UNIQUE,
    name NVARCHAR(200) COLLATE Latin1_General_100_CI_AI NOT NULL CONSTRAINT UQ_Allergen_name UNIQUE,
    is_active BIT NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Allergen_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_Allergen_updated_at DEFAULT SYSUTCDATETIME(),
    version ROWVERSION NOT NULL,
    CONSTRAINT CK_Allergen_text CHECK (LEN(LTRIM(RTRIM(code))) > 0 AND LEN(LTRIM(RTRIM(name))) > 0)
);

ALTER TABLE dbo.PatientAllergy ADD allergen_id BIGINT NULL, other_note NVARCHAR(1000) NULL;
GO
ALTER TABLE dbo.PatientAllergy ADD CONSTRAINT FK_PatientAllergy_allergen
    FOREIGN KEY (allergen_id) REFERENCES dbo.Allergen(id);

-- Existing free text is preserved; matching to a controlled substance requires a clinician.
UPDATE dbo.PatientAllergy SET other_note = substance WHERE allergen_id IS NULL;
ALTER TABLE dbo.PatientAllergy WITH CHECK ADD CONSTRAINT CK_PatientAllergy_source CHECK (
    (allergen_id IS NOT NULL AND other_note IS NULL)
    OR (allergen_id IS NULL AND other_note IS NOT NULL AND LEN(LTRIM(RTRIM(other_note))) > 0)
);
CREATE UNIQUE INDEX UX_PatientAllergy_active_allergen
    ON dbo.PatientAllergy(patient_id, allergen_id) WHERE is_active = 1 AND allergen_id IS NOT NULL;
