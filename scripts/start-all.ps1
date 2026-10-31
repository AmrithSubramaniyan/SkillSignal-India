# Start SkillSignal India (Backend + Frontend) on Windows PowerShell
$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot

if (Test-Path "C:\Users\God\.jdks\jdk-17.0.20.1+1") {
    $env:JAVA_HOME = "C:\Users\God\.jdks\jdk-17.0.20.1+1"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

Write-Host "Starting SkillSignal Backend on port 8080..."
$backendProc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c mvnw.cmd spring-boot:run" -WorkingDirectory "$RootDir\backend" -PassThru -WindowStyle Minimized
$backendProc.Id | Out-File "$RootDir\.backend.pid" -Encoding ascii

Write-Host "Starting SkillSignal Frontend on port 5173..."
$frontendProc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c npm run dev" -WorkingDirectory "$RootDir\frontend" -PassThru -WindowStyle Minimized
$frontendProc.Id | Out-File "$RootDir\.frontend.pid" -Encoding ascii

Write-Host "Backend PID: $($backendProc.Id) | Frontend PID: $($frontendProc.Id)"
Write-Host "Backend API: http://localhost:8080/api/health"
Write-Host "Frontend UI: http://localhost:5173"

