$ErrorActionPreference = 'Stop'
$previousMail = $env:MAIL_PASSWORD
$previousDb = $env:DB_PASSWORD
$previousAdmin = $env:BOOTSTRAP_ADMIN_EMAIL
function Read-PrivateValue([string]$prompt) {
    $secret = Read-Host $prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secret.Dispose() }
}
Push-Location (Join-Path $PSScriptRoot '..')
try {
    if (-not $env:MAIL_PASSWORD) { $env:MAIL_PASSWORD = Read-PrivateValue 'Contraseña del correo institucional (oculta)' }
    if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = Read-PrivateValue 'Contraseña de PostgreSQL (oculta)' }
    if (-not $env:BOOTSTRAP_ADMIN_EMAIL) { $env:BOOTSTRAP_ADMIN_EMAIL = Read-Host 'Correo del primer DECANO (Enter si ya existe)' }
    $jar = Join-Path (Get-Location) 'dist/inf513-proyecto1-1.0.0.jar'
    Write-Host 'Detenga cualquier instancia anterior antes de continuar. No se modifican tablas ajenas al prefijo p1_.'
    if ((Read-Host 'Escriba ACTUALIZAR para aplicar el esquema y arrancar') -cne 'ACTUALIZAR') { throw 'Operación cancelada.' }
    & java -cp $jar bo.edu.uagrm.inf513.grupo09sa.AplicarEsquema
    if ($LASTEXITCODE -ne 0) { throw 'La migración falló. El servicio no se inició.' }
    & (Join-Path $PSScriptRoot 'run-service.ps1')
} finally {
    Pop-Location
    $env:MAIL_PASSWORD = $previousMail
    $env:DB_PASSWORD = $previousDb
    $env:BOOTSTRAP_ADMIN_EMAIL = $previousAdmin
}
