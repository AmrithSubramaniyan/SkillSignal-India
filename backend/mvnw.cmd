@echo off
setlocal
if exist "C:\Users\God\.jdks\jdk-17.0.20.1+1\bin\java.exe" (
    set "JAVA_HOME=C:\Users\God\.jdks\jdk-17.0.20.1+1"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)
if exist "C:\apache-maven-3.9.14\bin\mvn.cmd" (
    call "C:\apache-maven-3.9.14\bin\mvn.cmd" %*
    exit /b %ERRORLEVEL%
)
call mvn %*
exit /b %ERRORLEVEL%

