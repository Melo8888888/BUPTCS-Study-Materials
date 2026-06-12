@echo off
setlocal
set WRAPPER_DIR=%~dp0.mvn\wrapper
set MAVEN_HOME=%WRAPPER_DIR%\apache-maven-3.9.9
set MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd
if not exist "%MAVEN_CMD%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $props=Get-Content '%WRAPPER_DIR%\maven-wrapper.properties' | Where-Object { $_ -match '^distributionUrl=' }; $url=$props -replace '^distributionUrl=',''; $zip='%WRAPPER_DIR%\apache-maven-3.9.9-bin.zip'; if (!(Test-Path $zip) -or ((Get-Item $zip).Length -eq 0)) { Invoke-WebRequest -Uri $url -OutFile $zip }; Expand-Archive -Path $zip -DestinationPath '%WRAPPER_DIR%' -Force"
)
call "%MAVEN_CMD%" %*
endlocal
