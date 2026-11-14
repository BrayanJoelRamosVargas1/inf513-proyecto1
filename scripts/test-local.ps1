param([string]$DatabaseUrl='jdbc:postgresql://127.0.0.1:5432/postgres',[string]$DatabaseUser='postgres')
$ErrorActionPreference='Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
$env:TEST_DB_URL=$DatabaseUrl
$env:TEST_DB_USER=$DatabaseUser
# Use TEST_DB_PASSWORD from the caller's private environment.
mvn --batch-mode --no-transfer-progress clean verify
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
