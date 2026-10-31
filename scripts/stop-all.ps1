# Stop SkillSignal India (Backend + Frontend) on Windows PowerShell
$RootDir = Split-Path -Parent $PSScriptRoot

foreach ($pidFile in @("$RootDir\.backend.pid", "$RootDir\.frontend.pid")) {
    if (Test-Path $pidFile) {
        $procId = Get-Content $pidFile | Select-Object -First 1
        if ($procId) {
            Write-Host "Stopping process tree for PID $procId..."
            cmd.exe /c "taskkill /PID $procId /T /F 2>nul" | Out-Null
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
}
Write-Host "All SkillSignal services stopped."

