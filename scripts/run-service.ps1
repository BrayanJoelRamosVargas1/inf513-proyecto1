$ErrorActionPreference = "Stop"
$jar = Join-Path $PSScriptRoot "..\target\inf513-proyecto1-1.0.0.jar"
if (-not $env:MAIL_PASSWORD -or -not $env:DB_PASSWORD) { throw "Defina MAIL_PASSWORD y DB_PASSWORD antes de iniciar." }
while ($true) {
    & java -jar $jar
    $code = $LASTEXITCODE
    if ($code -eq 0) { break }
    Write-Warning "El servicio terminó con código $code. Reinicio en 5 segundos."
    Start-Sleep -Seconds 5
}
