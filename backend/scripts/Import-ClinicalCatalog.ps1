param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('SuncareClinicMigrationTest', 'SuncareClinic')]
  [string]$Database
)

$ErrorActionPreference = 'Stop'
$seedPath = Join-Path $PSScriptRoot 'Seed-ClinicalCatalog.sql'
$seedConnection = New-Object System.Data.SqlClient.SqlConnection "Server=127.0.0.1,1433;Database=$Database;Integrated Security=True;Encrypt=True;TrustServerCertificate=True"
try {
  $seedConnection.Open()
  $seedCommand = $seedConnection.CreateCommand()
  $seedCommand.CommandTimeout = 120
  $seedCommand.CommandText = Get-Content -LiteralPath $seedPath -Raw -Encoding UTF8
  [void]$seedCommand.Parameters.Add('@ExpectedDatabase', [System.Data.SqlDbType]::NVarChar, 128)
  $seedCommand.Parameters['@ExpectedDatabase'].Value = $Database
  $seedReader = $seedCommand.ExecuteReader()
  try {
    while ($seedReader.Read()) {
      $seedRow = [ordered]@{}
      for ($seedIndex = 0; $seedIndex -lt $seedReader.FieldCount; $seedIndex++) {
        $seedRow[$seedReader.GetName($seedIndex)] = $seedReader.GetValue($seedIndex)
      }
      [pscustomobject]$seedRow
    }
  } finally { $seedReader.Close() }
} finally { $seedConnection.Dispose() }
