param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('SuncareClinicMigrationTest', 'SuncareClinic')]
  [string]$Database,
  [switch]$Rollback
)
$ErrorActionPreference = 'Stop'
$catalogPath = Join-Path $PSScriptRoot 'data/icd10-vn-2026.json'
$catalog = Get-Content -LiteralPath $catalogPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($catalog.system -ne 'ICD-10-VN' -or $catalog.version -ne '2026-07-01' -or $catalog.count -ne $catalog.items.Count -or $catalog.sourceSha256 -notmatch '^[a-f0-9]{64}$') { throw 'Invalid catalog metadata.' }
$table = New-Object System.Data.DataTable
foreach ($name in @('code', 'name', 'english_name')) { [void]$table.Columns.Add($name, [string]) }
foreach ($name in @('is_selectable', 'primary_allowed')) { [void]$table.Columns.Add($name, [bool]) }
[void]$table.Columns.Add('source_page', [int])
$codes = @{}
foreach ($item in $catalog.items) {
  $quarantined = $item.code -ceq 'U13/9' -and $item.sourceIssue -and -not $item.selectable -and -not $item.primaryAllowed -and $item.sourcePage -eq 1258
  if (($item.code -notmatch '^[A-Z][0-9]{2}(\.[A-Z0-9]{1,4})?$' -and -not $quarantined) -or $codes.ContainsKey($item.code) -or [string]::IsNullOrWhiteSpace($item.name) -or $item.name.Length -gt 1000 -or $item.englishName.Length -gt 1000 -or $item.sourcePage -lt 1 -or $item.sourcePage -gt $catalog.sourcePages) { throw "Invalid row $($item.code)" }
  $codes[$item.code] = $true
  [void]$table.Rows.Add($item.code, $item.name, $item.englishName, [bool]$item.selectable, [bool]$item.primaryAllowed, [int]$item.sourcePage)
}
$connection = New-Object System.Data.SqlClient.SqlConnection "Server=127.0.0.1,1433;Database=$Database;Integrated Security=True;Encrypt=True;TrustServerCertificate=True"
$connection.Open()
$transaction = $connection.BeginTransaction([System.Data.IsolationLevel]::Serializable)
try {
  $command = $connection.CreateCommand()
  $command.Transaction = $transaction
  $command.CommandTimeout = 120
  $command.CommandText = 'SELECT DB_NAME()'
  if ($command.ExecuteScalar() -ne $Database) { throw 'Unexpected database.' }
  $command.CommandText = 'CREATE TABLE #Catalog (code varchar(30) NOT NULL PRIMARY KEY,name nvarchar(1000),english_name nvarchar(1000),is_selectable bit,primary_allowed bit,source_page int)'
  [void]$command.ExecuteNonQuery()
  $bulk = New-Object System.Data.SqlClient.SqlBulkCopy($connection, [System.Data.SqlClient.SqlBulkCopyOptions]::Default, $transaction)
  $bulk.DestinationTableName = '#Catalog'
  foreach ($column in $table.Columns) { [void]$bulk.ColumnMappings.Add($column.ColumnName, $column.ColumnName) }
  $bulk.WriteToServer($table)
  $bulk.Close()
  foreach ($parameter in @('system', 'version', 'source', 'hash', 'correlation')) { [void]$command.Parameters.Add("@$parameter", [System.Data.SqlDbType]::NVarChar, 1000) }
  $command.Parameters['@system'].Value = $catalog.system
  $command.Parameters['@version'].Value = $catalog.version
  $command.Parameters['@source'].Value = $catalog.source
  $command.Parameters['@hash'].Value = $catalog.sourceSha256
  $command.Parameters['@correlation'].Value = [guid]::NewGuid().ToString()
  $command.CommandText = @'
IF EXISTS (SELECT 1 FROM dbo.DiagnosisConcept d WITH (UPDLOCK,HOLDLOCK) JOIN #Catalog s ON s.code=d.code
 WHERE d.code_system=@system AND d.code_version=@version AND
 (d.name COLLATE Latin1_General_100_BIN2<>s.name COLLATE Latin1_General_100_BIN2
 OR ISNULL(d.english_name,N'') COLLATE Latin1_General_100_BIN2<>s.english_name COLLATE Latin1_General_100_BIN2
 OR d.is_selectable<>s.is_selectable OR d.primary_allowed<>s.primary_allowed OR ISNULL(d.source_page,0)<>s.source_page))
 THROW 51000,'Existing catalog conflicts with source; no values overwritten.',1;
DECLARE @inserted TABLE (id bigint,code varchar(30));
INSERT dbo.DiagnosisConcept(code_system,code_version,code,name,english_name,is_active,is_selectable,primary_allowed,source_page)
OUTPUT inserted.id,inserted.code INTO @inserted
SELECT @system,@version,s.code,s.name,s.english_name,1,s.is_selectable,s.primary_allowed,s.source_page FROM #Catalog s
WHERE NOT EXISTS (SELECT 1 FROM dbo.DiagnosisConcept d WITH (UPDLOCK,HOLDLOCK) WHERE d.code_system=@system AND d.code_version=@version AND d.code=s.code);
INSERT dbo.AuditEvent(actor_id,actor_type,action,entity_type,entity_id,occurred_at,correlation_id,reason,redacted_change)
SELECT NULL,'SYSTEM','DIAGNOSIS_CATALOG_IMPORTED','DiagnosisConcept',id,SYSUTCDATETIME(),@correlation,
 N'Official ICD-10 VN annex import',
 (SELECT code AS code,@system AS codeSystem,@version AS codeVersion,@source AS source,@hash AS sourceSha256 FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)
FROM @inserted;
SELECT COUNT(*) FROM @inserted;
'@
  $inserted = [int]$command.ExecuteScalar()
  if ($Rollback) { $transaction.Rollback() } else { $transaction.Commit() }
  Write-Output "Database=$Database; sourceRows=$($table.Rows.Count); inserted=$inserted; unchanged=$($table.Rows.Count-$inserted); rollback=$Rollback"
} catch { $transaction.Rollback(); throw }
finally { $connection.Close() }
