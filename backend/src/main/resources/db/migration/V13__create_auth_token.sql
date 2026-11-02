CREATE TABLE dbo.AuthToken (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    expires_at DATETIME2(3) NOT NULL,
    revoked_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_AuthToken_Account FOREIGN KEY (account_id) REFERENCES dbo.[Account](id)
);

CREATE INDEX IX_AuthToken_account_id ON dbo.AuthToken(account_id);
