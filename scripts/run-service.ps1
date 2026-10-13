$ErrorActionPreference = "Stop"
$jar = Join-Path $PSScriptRoot "..\target\inf513-proyecto1-1.0.0.jar"
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { $jar = Join-Path $PSScriptRoot "..\dist\inf513-proyecto1-1.0.0.jar" }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "No se encontró el JAR. Ejecute mvn clean package o restaure dist/." }
if (-not $env:MAIL_PASSWORD -or -not $env:DB_PASSWORD) { throw "Defina MAIL_PASSWORD y DB_PASSWORD antes de iniciar." }
while ($true) {
    Push-Location (Join-Path $PSScriptRoot '..')
    try { & java -jar $jar } finally { Pop-Location }
    $code = $LASTEXITCODE
    if ($code -eq 0) { break }
    Write-Warning "El servicio terminó con código $code. Reinicio en 5 segundos."
    Start-Sleep -Seconds 5
}
