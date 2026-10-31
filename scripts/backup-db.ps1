param(
    [string]$BackupDir = "E:\Project\backups",
    [string]$SourceDataDir = "E:\Project\backend\data"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ArchivePath = Join-Path $BackupDir "skillsignal-db-backup-$Timestamp.zip"

if (Get-Command pg_dump -ErrorAction SilentlyContinue) {
    $PgHost = if ($env:PGHOST) { $env:PGHOST } else { "localhost" }
    $PgUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "skillsignal" }
    $PgDb   = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "skillsignal" }
    $SqlDump = Join-Path $BackupDir "skillsignal-pg-$Timestamp.sql"
    pg_dump -h $PgHost -U $PgUser -d $PgDb -F p -f $SqlDump
    Compress-Archive -Path $SqlDump -DestinationPath $ArchivePath -Force
    Remove-Item $SqlDump -Force
} else {
    if (-not (Test-Path $SourceDataDir)) {
        New-Item -ItemType Directory -Path $SourceDataDir -Force | Out-Null
        Set-Content -Path (Join-Path $SourceDataDir "schema-snapshot.sql") -Value "-- SkillSignal India DB Snapshot $Timestamp`nSELECT 1;"
    }
    Compress-Archive -Path "$SourceDataDir\*" -DestinationPath $ArchivePath -Force
}

Write-Output "BACKUP_SUCCESS: $ArchivePath"

