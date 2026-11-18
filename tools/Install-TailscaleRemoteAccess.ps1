$ErrorActionPreference = "Stop"

$principal = New-Object Security.Principal.WindowsPrincipal(
    [Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Запустите этот скрипт из PowerShell от имени администратора."
}

$tailscaleExe = "C:\Program Files\Tailscale\tailscale.exe"
$upsmonWebServer = "C:\Program Files (x86)\UPSMONPRO\UPSMONWebSer\UPSMONWebserver.exe"
if (-not (Test-Path -LiteralPath $tailscaleExe -PathType Leaf)) {
    throw "Tailscale не найден: $tailscaleExe"
}
if (-not (Test-Path -LiteralPath $upsmonWebServer -PathType Leaf)) {
    throw "Веб-сервер UPSMON PRO не найден: $upsmonWebServer"
}

$statusJson = & $tailscaleExe status --json
if ($LASTEXITCODE -ne 0) {
    throw "Не удалось получить состояние Tailscale. Войдите в tailnet на этом компьютере."
}
$tailscaleStatus = $statusJson | ConvertFrom-Json
if ($tailscaleStatus.BackendState -ne "Running") {
    throw "Tailscale не подключён. Войдите в tailnet на этом компьютере и повторите запуск."
}
$tailscaleIp = @($tailscaleStatus.Self.TailscaleIPs |
    Where-Object { $_ -match '^100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.' } |
    Select-Object -First 1)
if ($tailscaleIp.Count -eq 0) {
    throw "Активный Tailscale IPv4-адрес не найден."
}

$tailscaleRange = "100.64.0.0/10"
$powershellExe = (Get-Process -Id $PID).Path
$historyRemoteAddresses = @("LocalSubnet", $tailscaleRange)

Remove-NetFirewallRule -DisplayName "MeVolt UPS Event History TCP 8001" `
    -ErrorAction SilentlyContinue
New-NetFirewallRule `
    -DisplayName "MeVolt UPS Event History TCP 8001" `
    -Description "Read-only UPS event history; accessible only on LAN and authenticated Tailscale tailnet." `
    -Direction Inbound `
    -Action Allow `
    -Enabled True `
    -Profile Any `
    -Program $powershellExe `
    -Protocol TCP `
    -LocalPort 8001 `
    -RemoteAddress $historyRemoteAddresses | Out-Null

Remove-NetFirewallRule -DisplayName "MeVolt UPSMON Web TCP 8000 over Tailscale" `
    -ErrorAction SilentlyContinue
New-NetFirewallRule `
    -DisplayName "MeVolt UPSMON Web TCP 8000 over Tailscale" `
    -Description "UPSMON telemetry; accessible only from the authenticated Tailscale tailnet." `
    -Direction Inbound `
    -Action Allow `
    -Enabled True `
    -Profile Any `
    -Program $upsmonWebServer `
    -Protocol TCP `
    -LocalPort 8000 `
    -RemoteAddress $tailscaleRange | Out-Null

Write-Output ("Tailscale IPv4: {0}" -f $tailscaleIp[0])
Write-Output "Installed TCP 8000/8001 firewall rules restricted to Tailscale; port 8001 also remains available on the local subnet."
Write-Output "No router port forwarding was added."
