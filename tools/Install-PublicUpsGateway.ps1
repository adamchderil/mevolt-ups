$ErrorActionPreference = "Stop"

$gatewayScript = Join-Path $PSScriptRoot "Serve-PublicUpsGateway.ps1"
$tailscale = Get-Command "tailscale.exe" -ErrorAction SilentlyContinue
if ($null -eq $tailscale) {
    throw "tailscale.exe was not found. Install and connect Tailscale on this computer."
}
if (-not (Test-Path -LiteralPath $gatewayScript -PathType Leaf)) {
    throw "Gateway script was not found: $gatewayScript"
}

$beforeStatus = (& $tailscale.Source funnel status 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw "Could not read the current Tailscale Funnel configuration: $beforeStatus"
}
if ($beforeStatus -notmatch "3001") {
    throw "The existing Funnel route to port 3001 was not confirmed. No routes were changed."
}

$gatewayProcesses = @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" |
    Where-Object { $_.CommandLine -like "*Serve-PublicUpsGateway.ps1*" })
$portCheck = New-Object System.Net.Sockets.TcpClient
$portInUse = $false
try {
    $connectResult = $portCheck.BeginConnect([System.Net.IPAddress]::Loopback, 8002, $null, $null)
    $portCheck.EndConnect($connectResult)
    $portInUse = $true
} catch {
    $portInUse = $false
} finally {
    $portCheck.Close()
}

if ($gatewayProcesses.Count -eq 0) {
    if ($portInUse) {
        throw "Port 8002 is occupied by an unknown process. Funnel was not changed."
    }
    $accountFile = Join-Path (Join-Path $env:APPDATA "MeVoltUpsMonitor") "users.json"
    if (-not (Test-Path -LiteralPath $accountFile -PathType Leaf)) {
        $securePassword = Read-Host `
            "Create the initial admin password (at least 12 characters)" -AsSecureString
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
            $securePassword)
        try {
            $initialPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
                $passwordPointer)
            if ([string]::IsNullOrWhiteSpace($initialPassword) -or $initialPassword.Length -lt 12) {
                throw "The initial admin password must contain at least 12 characters."
            }
            $env:MEVOLT_INITIAL_ADMIN_PASSWORD = $initialPassword
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
            $securePassword.Dispose()
            $initialPassword = $null
        }
    } else {
        Remove-Item Env:\MEVOLT_INITIAL_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    }

    $powerShellExe = Join-Path $PSHOME "powershell.exe"
    $argumentList = @(
        "-NoProfile",
        "-WindowStyle Hidden",
        "-ExecutionPolicy Bypass",
        "-File `"$gatewayScript`""
    )
    try {
        Start-Process -FilePath $powerShellExe `
            -ArgumentList $argumentList -WindowStyle Hidden | Out-Null
    } finally {
        Remove-Item Env:\MEVOLT_INITIAL_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    }

    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        Start-Sleep -Milliseconds 250
        $probe = New-Object System.Net.Sockets.TcpClient
        try {
            $probe.Connect([System.Net.IPAddress]::Loopback, 8002)
            $ready = $true
        } catch {
            $ready = $false
        } finally {
            $probe.Close()
        }
        if ($ready) {
            break
        }
    }
    if (-not $ready) {
        throw "The gateway did not start at 127.0.0.1:8002. Check the UPSMON PRO and event log paths."
    }
}

$funnelOutput = (& $tailscale.Source funnel --bg --set-path=/upsmon `
    http://127.0.0.1:8002 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw "Could not add the /upsmon route to Tailscale Funnel: $funnelOutput"
}

$afterStatus = (& $tailscale.Source funnel status 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw "Could not verify Funnel after configuration: $afterStatus"
}
if ($afterStatus -notmatch "3001" -or $afterStatus -notmatch "8002") {
    throw "Funnel status did not confirm both the existing :3001 route and new :8002 route. Check manually:`n$afterStatus"
}

$runKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
$powerShellExe = Join-Path $PSHOME "powershell.exe"
$startupCommand = "`"$powerShellExe`" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$gatewayScript`""
New-ItemProperty -Path $runKey -Name "MeVoltPublicUpsGateway" `
    -Value $startupCommand -PropertyType String -Force | Out-Null

Write-Output "Gateway is running at 127.0.0.1:8002 and registered for startup."
Write-Output "Public HTTPS path: https://win-pjghmomb24s.tail184144.ts.net/upsmon"
Write-Output "The existing Funnel root route to :3001 is preserved."
