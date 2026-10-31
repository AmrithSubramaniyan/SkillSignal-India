param(
    [Parameter(Mandatory=$true)]
    [string]$ArchivePath,
    [string]$RestoreTargetDir = "E:\Project\backups\restored-verify"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $ArchivePath)) {
    throw "Backup archive not found: $ArchivePath"
}

if (Test-Path $RestoreTargetDir) {
    Remove-Item -Path $RestoreTargetDir -Recurse -Force
}
New-Item -ItemType Directory -Path $RestoreTargetDir -Force | Out-Null

Expand-Archive -Path $ArchivePath -DestinationPath $RestoreTargetDir -Force
$RestoredFiles = Get-ChildItem -Path $RestoreTargetDir -Recurse -File
if ($RestoredFiles.Count -eq 0) {
    throw "Restore verification failed: 0 files extracted from $ArchivePath"
}

Write-Output "RESTORE_SUCCESS: $($RestoredFiles.Count) file(s) restored to $RestoreTargetDir"

