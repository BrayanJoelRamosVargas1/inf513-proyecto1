param([Parameter(Mandatory=$true)][string]$CredentialPath)
$ErrorActionPreference='Stop'
$taskRepo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskSecrets=Import-Clixml -LiteralPath $CredentialPath
$env:MAIL_PASSWORD=([pscredential]::new('mail',$taskSecrets.Mail)).GetNetworkCredential().Password
$env:DB_PASSWORD=([pscredential]::new('db',$taskSecrets.Database)).GetNetworkCredential().Password
$taskLogs=Join-Path $taskRepo 'logs'
New-Item -ItemType Directory -Path $taskLogs -Force | Out-Null
$env:LOG_DIR=$taskLogs
$taskJar=Join-Path $taskRepo 'dist/inf513-proyecto1-1.0.0.jar'
$taskJava=(Get-Command java.exe -ErrorAction Stop).Source
try {
    while($true){
        $taskProcess=Start-Process -FilePath $taskJava -ArgumentList @('-jar',('"'+$taskJar+'"')) -WorkingDirectory $taskRepo -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskLogs 'servicio-salida.log') -RedirectStandardError (Join-Path $taskLogs 'servicio-error.log') -PassThru
        @{Pid=$taskProcess.Id;Started=(Get-Date).ToString('o');JarSHA256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path (Split-Path -Parent $CredentialPath) 'servicio-estado.json') -Encoding utf8
        $taskProcess.WaitForExit()
        if($taskProcess.ExitCode -eq 0){break}
        Start-Sleep -Seconds 5
    }
} finally {
    Remove-Item Env:MAIL_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
