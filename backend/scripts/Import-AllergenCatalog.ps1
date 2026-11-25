param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('SuncareClinicMigrationTest', 'SuncareClinic')]
  [string]$Database,
  [switch]$Rollback
)

$ErrorActionPreference = 'Stop'
$catalogPath = Join-Path $PSScriptRoot 'data/common-allergens.json'
$catalog = Get-Content -LiteralPath $catalogPath -Raw -Encoding UTF8 | ConvertFrom-Json
$catalogCodes = @{}
foreach ($item in $catalog.items) {
  if ($item.code -notmatch '^[A-Z0-9_-]{1,40}$' -or [string]::IsNullOrWhiteSpace($item.name) -or $item.name.Length -gt 200) {
    throw "Invalid catalog row: $($item.code)"
  }
  if ($catalogCodes.ContainsKey($item.code)) { throw "Duplicate input code: $($item.code)" }
  $catalogCodes[$item.code] = $true
  if (-not $catalog.sources.($item.source)) { throw "Missing source: $($item.code)" }
}

$catalogConnection = New-Object System.Data.SqlClient.SqlConnection "Server=127.0.0.1,1433;Database=$Database;Integrated Security=True;Encrypt=True;TrustServerCertificate=True"
$catalogConnection.Open()
$catalogTransaction = $catalogConnection.BeginTransaction([System.Data.IsolationLevel]::Serializable)
$inserted = 0
$skipped = 0
$correlationId = [guid]::NewGuid().ToString()
try {
  $catalogCommand = $catalogConnection.CreateCommand()
  $catalogCommand.Transaction = $catalogTransaction
  $catalogCommand.CommandText = 'SELECT DB_NAME()'
  if ($catalogCommand.ExecuteScalar() -ne $Database) { throw 'Connected to an unexpected database.' }
  $catalogCommand.CommandText = 'SELECT COUNT(*) FROM dbo.Allergen'
  $beforeCount = [int]$catalogCommand.ExecuteScalar()
  foreach ($item in $catalog.items) {
    $catalogCommand.Parameters.Clear()
    $catalogCommand.CommandText = 'SELECT code,name FROM dbo.Allergen WITH (UPDLOCK,HOLDLOCK) WHERE code=@code OR name=@name'
    [void]$catalogCommand.Parameters.Add('@code', [System.Data.SqlDbType]::VarChar, 40)
    [void]$catalogCommand.Parameters.Add('@name', [System.Data.SqlDbType]::NVarChar, 200)
    $catalogCommand.Parameters['@code'].Value = $item.code
    $catalogCommand.Parameters['@name'].Value = $item.name
    $catalogReader = $catalogCommand.ExecuteReader()
    $matches = @()
    while ($catalogReader.Read()) { $matches += @{ code = $catalogReader.GetString(0); name = $catalogReader.GetString(1) } }
    $catalogReader.Close()
    if ($matches.Count -gt 0) {
      if ($matches.Count -ne 1 -or $matches[0].code -cne $item.code -or $matches[0].name -cne $item.name) {
        throw "Catalog conflict for $($item.code); existing values were not overwritten."
      }
      # Preserve existing activation state and rowversion, including deliberately disabled rows.
      $skipped++
      continue
    }
    $catalogCommand.CommandText = 'INSERT INTO dbo.Allergen (code,name,is_active) OUTPUT INSERTED.id VALUES (@code,@name,1)'
    $allergenId = [long]$catalogCommand.ExecuteScalar()
    $catalogCommand.Parameters.Clear()
    $catalogCommand.CommandText = @'
INSERT INTO dbo.AuditEvent (actor_id,actor_type,action,entity_type,entity_id,occurred_at,correlation_id,reason,redacted_change)
VALUES (NULL,'SYSTEM','ALLERGEN_CATALOG_IMPORTED','Allergen',@id,SYSUTCDATETIME(),@correlation,@reason,@detail)
'@
    [void]$catalogCommand.Parameters.Add('@id', [System.Data.SqlDbType]::BigInt)
    [void]$catalogCommand.Parameters.Add('@correlation', [System.Data.SqlDbType]::VarChar, 100)
    [void]$catalogCommand.Parameters.Add('@reason', [System.Data.SqlDbType]::NVarChar, 1000)
    [void]$catalogCommand.Parameters.Add('@detail', [System.Data.SqlDbType]::NVarChar, -1)
    $catalogCommand.Parameters['@id'].Value = $allergenId
    $catalogCommand.Parameters['@correlation'].Value = $correlationId
    $catalogCommand.Parameters['@reason'].Value = 'User-requested common allergen master-data import; local Suncare codes.'
    $catalogCommand.Parameters['@detail'].Value = (@{ code = $item.code; name = $item.name; source = $catalog.sources.($item.source); catalogVersion = $catalog.version } | ConvertTo-Json -Compress)
    [void]$catalogCommand.ExecuteNonQuery()
    $inserted++
  }
  $catalogCommand.Parameters.Clear()
  $catalogCommand.CommandText = 'SELECT COUNT(*) FROM dbo.Allergen'
  $afterCount = [int]$catalogCommand.ExecuteScalar()
  if ($afterCount -ne $beforeCount + $inserted) { throw 'Catalog count verification failed.' }
  if ($Rollback) { $catalogTransaction.Rollback() } else { $catalogTransaction.Commit() }
  [pscustomobject]@{ database = $Database; inserted = $inserted; skipped = $skipped; beforeCount = $beforeCount; afterCountInTransaction = $afterCount; rolledBack = [bool]$Rollback }
} catch {
  try { $catalogTransaction.Rollback() } catch { }
  throw
} finally {
  if ($catalogCommand) { $catalogCommand.Dispose() }
  $catalogTransaction.Dispose()
  $catalogConnection.Dispose()
}
