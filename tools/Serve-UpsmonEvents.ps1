param(
    [string]$UpsmonDataDirectory = "C:\Users\Public\UPSMON-Pro",
    [int]$Port = 8001
)

$principal = New-Object Security.Principal.WindowsPrincipal(
    [Security.Principal.WindowsIdentity]::GetCurrent())
$isAdministrator = $principal.IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if ($Port -lt 1 -or $Port -gt 65535) {
    throw "Порт должен быть в диапазоне от 1 до 65535."
}
if (-not (Test-Path -LiteralPath $UpsmonDataDirectory -PathType Container)) {
    throw "Папка журнала UPSMON PRO не найдена: $UpsmonDataDirectory"
}

$ruleName = "MeVolt UPS Event History TCP $Port"
$allowedRemoteAddresses = @("LocalSubnet", "100.64.0.0/10")
$programPath = (Get-Process -Id $PID).Path
$firewallRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if ($null -eq $firewallRule) {
    if ($isAdministrator) {
        New-NetFirewallRule `
            -DisplayName $ruleName `
            -Description "Read-only UPSMON event history, restricted to the local subnet." `
            -Direction Inbound `
            -Action Allow `
            -Enabled True `
            -Profile Any `
            -Program $programPath `
            -Protocol TCP `
            -LocalPort $Port `
            -RemoteAddress $allowedRemoteAddresses | Out-Null
        Write-Output "Created a firewall rule restricted to local and Tailscale networks."
    } else {
        Write-Warning "No firewall rule exists for TCP $Port. The server will run, but remote clients may not connect until an administrator installs the restricted firewall rules."
    }
} else {
    $addressFilter = $firewallRule | Get-NetFirewallAddressFilter
    $portFilter = $firewallRule | Get-NetFirewallPortFilter
    $isExpectedPort = ($portFilter.Protocol -eq "TCP") -and
        ($portFilter.LocalPort -eq [string]$Port)
    $currentRemoteAddresses = @($addressFilter.RemoteAddress)
    $isLocalSubnetOnly = ($currentRemoteAddresses.Count -eq 2) -and
        ($currentRemoteAddresses -contains "LocalSubnet") -and
        ($currentRemoteAddresses -contains "100.64.0.0/10")
    $isSafeRule = ($firewallRule.Action -eq "Allow") -and
        $isExpectedPort -and $isLocalSubnetOnly
    if (-not $isSafeRule) {
        throw "The existing firewall rule '$ruleName' is not restricted to TCP $Port from LocalSubnet and Tailscale. Review it before starting the server."
    }
}

function Send-HttpResponse {
    param(
        [System.IO.Stream]$Stream,
        [int]$StatusCode,
        [string]$StatusText,
        [string]$ContentType,
        [string]$Body
    )

    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $headers = "HTTP/1.1 $StatusCode $StatusText`r`n" +
        "Content-Type: $ContentType; charset=utf-8`r`n" +
        "Content-Length: $($bodyBytes.Length)`r`n" +
        "Cache-Control: no-store`r`n" +
        "Connection: close`r`n`r`n"
    $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($headers)
    $Stream.Write($headerBytes, 0, $headerBytes.Length)
    $Stream.Write($bodyBytes, 0, $bodyBytes.Length)
    $Stream.Flush()
}

$listener = New-Object System.Net.Sockets.TcpListener(
    [System.Net.IPAddress]::Any, $Port)
$windows1251 = [System.Text.Encoding]::GetEncoding(1251)
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
$maximumLogSize = 1024 * 1024

try {
    $listener.Start()
    Write-Output "Сервер истории слушает TCP $Port; доступ разрешается правилами брандмауэра только из локальной сети и Tailscale."
    Write-Output "GET /events.csv возвращает оригинальный EventRecord.CSV. Нажмите Ctrl+C для остановки."

    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $client.ReceiveTimeout = 5000
            $client.SendTimeout = 5000
            $networkStream = $client.GetStream()
            $requestReader = New-Object System.IO.StreamReader(
                $networkStream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
            $requestLine = $requestReader.ReadLine()
            $headerCount = 0
            while ($null -ne ($headerLine = $requestReader.ReadLine())) {
                if ($headerLine.Length -eq 0) {
                    break
                }
                $headerCount++
                if ($headerCount -gt 32) {
                    break
                }
            }

            if ($headerCount -gt 32 -or [string]::IsNullOrWhiteSpace($requestLine)) {
                Send-HttpResponse $networkStream 400 "Bad Request" "text/plain" "Invalid request."
            } else {
                $requestParts = $requestLine.Split(' ')
                if ($requestParts.Length -lt 2 -or $requestParts[0] -ne "GET") {
                    Send-HttpResponse $networkStream 405 "Method Not Allowed" `
                        "text/plain" "Only GET is supported."
                } elseif ($requestParts[1] -ne "/events.csv") {
                    Send-HttpResponse $networkStream 404 "Not Found" "text/plain" "Not found."
                } else {
                    $latestRecord = Get-ChildItem -LiteralPath $UpsmonDataDirectory `
                        -Filter "EventRecord.CSV" -File -Recurse |
                        Sort-Object LastWriteTimeUtc -Descending |
                        Select-Object -First 1

                    if ($null -eq $latestRecord) {
                        Send-HttpResponse $networkStream 503 "Service Unavailable" `
                            "text/plain" "Event log is not available."
                    } elseif ($latestRecord.Length -gt $maximumLogSize) {
                        Send-HttpResponse $networkStream 413 "Payload Too Large" `
                            "text/plain" "Event log is too large."
                    } else {
                        $fileStream = [System.IO.File]::Open(
                            $latestRecord.FullName,
                            [System.IO.FileMode]::Open,
                            [System.IO.FileAccess]::Read,
                            [System.IO.FileShare]::ReadWrite)
                        try {
                            $eventReader = New-Object System.IO.StreamReader(
                                $fileStream, $windows1251, $true)
                            $eventText = $eventReader.ReadToEnd()
                            $eventReader.Dispose()
                        } finally {
                            $fileStream.Dispose()
                        }

                        $body = [System.Text.Encoding]::UTF8.GetString(
                            $utf8WithoutBom.GetBytes($eventText))
                        if ([System.Text.Encoding]::UTF8.GetByteCount($body) -gt $maximumLogSize) {
                            Send-HttpResponse $networkStream 413 "Payload Too Large" `
                                "text/plain" "Event log is too large."
                        } else {
                            Send-HttpResponse $networkStream 200 "OK" "text/csv" $body
                        }
                    }
                }
            }
        } catch {
            Write-Warning ("Request failed: {0}" -f $_.Exception.Message)
        } finally {
            $client.Close()
        }
    }
} finally {
    $listener.Stop()
}
