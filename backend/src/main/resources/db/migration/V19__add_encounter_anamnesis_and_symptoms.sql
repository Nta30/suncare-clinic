-- Patient interview belongs to the Encounter; previsit declarations remain independent.
ALTER TABLE dbo.Encounter ADD history_of_present_illness NVARCHAR(MAX) NULL;

CREATE TABLE dbo.Symptom (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Symptom PRIMARY KEY,
    name NVARCHAR(200) NOT NULL,
    normalized_name AS LOWER(REPLACE(name COLLATE Latin1_General_100_CI_AI, N'đ', N'd')) PERSISTED,
    is_active BIT NOT NULL CONSTRAINT DF_Symptom_active DEFAULT 1,
    created_at DATETIME2(3) NOT NULL CONSTRAINT DF_Symptom_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_Symptom_name CHECK (LEN(LTRIM(RTRIM(name))) > 0)
);
CREATE UNIQUE INDEX UX_Symptom_name ON dbo.Symptom(normalized_name);

CREATE TABLE dbo.EncounterSymptom (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_EncounterSymptom PRIMARY KEY,
    encounter_id BIGINT NOT NULL,
    symptom_id BIGINT NULL,
    name_snapshot NVARCHAR(200) NOT NULL,
    normalized_name NVARCHAR(200) COLLATE Latin1_General_100_BIN2 NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT FK_EncounterSymptom_encounter FOREIGN KEY (encounter_id) REFERENCES dbo.Encounter(id),
    CONSTRAINT FK_EncounterSymptom_symptom FOREIGN KEY (symptom_id) REFERENCES dbo.Symptom(id),
    CONSTRAINT UQ_EncounterSymptom_name UNIQUE (encounter_id, normalized_name),
    CONSTRAINT UQ_EncounterSymptom_order UNIQUE (encounter_id, display_order),
    CONSTRAINT CK_EncounterSymptom_content CHECK (LEN(name_snapshot)>0 AND LEN(normalized_name)>0 AND display_order>0)
);
-- No invented/shared symptom data. Approved catalogue data can be imported separately.
-- Replacement of encounter selections is audited with the version-checked notes transaction.
