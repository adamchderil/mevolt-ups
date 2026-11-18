param(
    [string]$UpsmonDataDirectory = "C:\Users\Public\UPSMON-Pro",
    [string]$UpsmonWebRoot = "C:\Program Files (x86)\UPSMONPRO\UPSMONWebSer",
    [int]$Port = 8002
)

$ErrorActionPreference = "Stop"
$iterations = 120000
$hashLength = 32
$maximumRequestBody = 4096
$maximumEventLog = 1024 * 1024
$script:failedAuthAttempts = @{}
$accountDirectory = Join-Path $env:APPDATA "MeVoltUpsMonitor"
$accountFile = Join-Path $accountDirectory "users.json"

if ($Port -lt 1 -or $Port -gt 65535) {
    throw "Порт должен быть в диапазоне от 1 до 65535."
}
if (-not (Test-Path -LiteralPath $UpsmonDataDirectory -PathType Container)) {
    throw "Папка журнала UPSMON PRO не найдена: $UpsmonDataDirectory"
}
if (-not (Test-Path -LiteralPath $UpsmonWebRoot -PathType Container)) {
    throw "Папка веб-сервера UPSMON PRO не найдена: $UpsmonWebRoot"
}

if (-not (Test-Path -LiteralPath $accountFile -PathType Leaf)) {
    $bootstrapPassword = $env:MEVOLT_INITIAL_ADMIN_PASSWORD
    $salt = New-Object byte[] 16
    $hash = $null
    $derive = $null
    try {
        if ([string]::IsNullOrWhiteSpace($bootstrapPassword) -or
                $bootstrapPassword.Length -lt 12) {
            throw "Initialize the gateway with Install-PublicUpsGateway.ps1 and set an admin password of at least 12 characters."
        }

        $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $random.GetBytes($salt)
        } finally {
            $random.Dispose()
        }

        $derive = [System.Security.Cryptography.Rfc2898DeriveBytes]::new(
            $bootstrapPassword,
            $salt,
            $iterations,
            [System.Security.Cryptography.HashAlgorithmName]::SHA256)
        $hash = $derive.GetBytes($hashLength)
        $saltHex = [BitConverter]::ToString($salt).Replace("-", "").ToLowerInvariant()
        $hashHex = [BitConverter]::ToString($hash).Replace("-", "").ToLowerInvariant()
        $initialState = [ordered]@{
            Version = 1
            Users = @(
                [ordered]@{
                    Username = "admin"
                    Role = "admin"
                    Salt = $saltHex
                    Hash = $hashHex
                }
            )
        }
        New-Item -ItemType Directory -Path $accountDirectory -Force | Out-Null
        $initialJson = ConvertTo-Json -InputObject $initialState -Depth 4
        [System.IO.File]::WriteAllText(
            $accountFile, $initialJson, (New-Object System.Text.UTF8Encoding($false)))
    } finally {
        if ($null -ne $derive) {
            $derive.Dispose()
        }
        [Array]::Clear($salt, 0, $salt.Length)
        if ($null -ne $hash) {
            [Array]::Clear($hash, 0, $hash.Length)
        }
        $bootstrapPassword = $null
        Remove-Item Env:\MEVOLT_INITIAL_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    }
}

function Read-UserState {
    $state = Get-Content -LiteralPath $accountFile -Raw -Encoding UTF8 |
        ConvertFrom-Json
    if ($state.Version -ne 1 -or $null -eq $state.Users) {
        throw "Файл учётных записей MeVolt повреждён или имеет неподдерживаемую версию."
    }
    return @($state.Users)
}

function Write-UserState {
    param([object[]]$Users)

    $state = [ordered]@{
        Version = 1
        Users = @($Users)
    }
    $json = ConvertTo-Json -InputObject $state -Depth 4
    $temporaryFile = Join-Path $accountDirectory ("users.{0}.tmp" -f $PID)
    [System.IO.File]::WriteAllText(
        $temporaryFile, $json, (New-Object System.Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $temporaryFile -Destination $accountFile -Force
}

function ConvertFrom-Hex {
    param([string]$Value)

    if ([string]::IsNullOrEmpty($Value) -or ($Value.Length % 2 -ne 0)) {
        throw "Invalid hexadecimal credential data."
    }
    $bytes = New-Object byte[] ($Value.Length / 2)
    for ($index = 0; $index -lt $bytes.Length; $index++) {
        $bytes[$index] = [Convert]::ToByte($Value.Substring($index * 2, 2), 16)
    }
    return ,$bytes
}

function ConvertTo-Hex {
    param([byte[]]$Value)

    return [BitConverter]::ToString($Value).Replace("-", "").ToLowerInvariant()
}

function Test-UserPassword {
    param(
        [object]$User,
        [string]$Password
    )

    try {
        $salt = ConvertFrom-Hex $User.Salt
        $expectedHash = ConvertFrom-Hex $User.Hash
        if ($salt.Length -ne 16 -or $expectedHash.Length -ne $hashLength) {
            return $false
        }

        $derive = [System.Security.Cryptography.Rfc2898DeriveBytes]::new(
            $Password,
            $salt,
            $iterations,
            [System.Security.Cryptography.HashAlgorithmName]::SHA256)
        try {
            $actualHash = $derive.GetBytes($hashLength)
            $difference = 0
            for ($index = 0; $index -lt $hashLength; $index++) {
                $difference = $difference -bor ($actualHash[$index] -bxor $expectedHash[$index])
            }
            [Array]::Clear($actualHash, 0, $actualHash.Length)
            return $difference -eq 0
        } finally {
            $derive.Dispose()
            [Array]::Clear($salt, 0, $salt.Length)
            [Array]::Clear($expectedHash, 0, $expectedHash.Length)
        }
    } catch {
        return $false
    }
}

function Get-RecentFailedAttempts {
    param([string]$Username)

    $cutoff = [DateTime]::UtcNow.AddMinutes(-10)
    if (-not $script:failedAuthAttempts.ContainsKey($Username)) {
        return @()
    }
    return @($script:failedAuthAttempts[$Username] |
        Where-Object { $_ -gt $cutoff })
}

function Record-FailedAttempt {
    param([string]$Username)

    $recent = @(Get-RecentFailedAttempts $Username) + @([DateTime]::UtcNow)
    $script:failedAuthAttempts[$Username] = $recent
}

function New-UserRecord {
    param(
        [string]$Username,
        [string]$Password
    )

    $salt = New-Object byte[] 16
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($salt)
    } finally {
        $random.Dispose()
    }

    $derive = [System.Security.Cryptography.Rfc2898DeriveBytes]::new(
        $Password,
        $salt,
        $iterations,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256)
    try {
        $hash = $derive.GetBytes($hashLength)
        $record = [ordered]@{
            Username = $Username
            Role = "user"
            Salt = ConvertTo-Hex $salt
            Hash = ConvertTo-Hex $hash
        }
        [Array]::Clear($hash, 0, $hash.Length)
        return $record
    } finally {
        $derive.Dispose()
        [Array]::Clear($salt, 0, $salt.Length)
    }
}

function Read-HttpLine {
    param([System.IO.Stream]$Stream)

    $lineBytes = New-Object System.IO.MemoryStream
    try {
        while ($true) {
            $nextByte = $Stream.ReadByte()
            if ($nextByte -eq -1 -or $nextByte -eq 10) {
                break
            }
            if ($nextByte -ne 13) {
                $lineBytes.WriteByte([byte]$nextByte)
            }
            if ($lineBytes.Length -gt 8192) {
                throw "HTTP line is too long."
            }
        }
        if ($lineBytes.Length -eq 0 -and $nextByte -eq -1) {
            return $null
        }
        return [System.Text.Encoding]::ASCII.GetString($lineBytes.ToArray())
    } finally {
        $lineBytes.Dispose()
    }
}

function Read-RequestBody {
    param(
        [System.IO.Stream]$Stream,
        [int]$Length
    )

    $bytes = New-Object byte[] $Length
    $offset = 0
    while ($offset -lt $Length) {
        $read = $Stream.Read($bytes, $offset, $Length - $offset)
        if ($read -le 0) {
            throw "HTTP request body ended unexpectedly."
        }
        $offset += $read
    }
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Send-HttpResponse {
    param(
        [System.IO.Stream]$Stream,
        [int]$StatusCode,
        [string]$StatusText,
        [string]$ContentType,
        [string]$Body,
        [string]$Role = ""
    )

    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $headers = "HTTP/1.1 $StatusCode $StatusText`r`n" +
        "Content-Type: $ContentType; charset=utf-8`r`n" +
        "Content-Length: $($bodyBytes.Length)`r`n" +
        "Cache-Control: no-store`r`n" +
        "Connection: close`r`n"
    if ($StatusCode -eq 401) {
        $headers += "WWW-Authenticate: Basic realm=`"MeVolt UPS`", charset=`"UTF-8`"`r`n"
    }
    if (-not [string]::IsNullOrEmpty($Role)) {
        $headers += "X-MeVolt-Role: $Role`r`n"
    }
    $headers += "`r`n"
    $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($headers)
    $Stream.Write($headerBytes, 0, $headerBytes.Length)
    $Stream.Write($bodyBytes, 0, $bodyBytes.Length)
    $Stream.Flush()
}

function Handle-Client {
    param([System.Net.Sockets.TcpClient]$Client)

    $stream = $Client.GetStream()
    $Client.ReceiveTimeout = 5000
    $Client.SendTimeout = 5000
    $requestLine = Read-HttpLine $stream
    if ([string]::IsNullOrWhiteSpace($requestLine)) {
        return
    }

    $requestParts = $requestLine.Split(' ')
    if ($requestParts.Length -ne 3 -or $requestParts[2] -notmatch '^HTTP/1\.[01]$') {
        Send-HttpResponse $stream 400 "Bad Request" "text/plain" "Invalid request."
        return
    }

    $headers = @{}
    for ($headerIndex = 0; $headerIndex -lt 32; $headerIndex++) {
        $headerLine = Read-HttpLine $stream
        if ($null -eq $headerLine) {
            Send-HttpResponse $stream 400 "Bad Request" "text/plain" "Invalid headers."
            return
        }
        if ($headerLine.Length -eq 0) {
            break
        }
        $separator = $headerLine.IndexOf(':')
        if ($separator -le 0) {
            Send-HttpResponse $stream 400 "Bad Request" "text/plain" "Invalid header."
            return
        }
        $headers[$headerLine.Substring(0, $separator).Trim()] =
            $headerLine.Substring($separator + 1).Trim()
    }
    if ($headerIndex -ge 32) {
        Send-HttpResponse $stream 431 "Request Header Fields Too Large" `
            "text/plain" "Too many headers."
        return
    }

    $method = $requestParts[0].ToUpperInvariant()
    $path = $requestParts[1].Split('?')[0]
    if ($path.StartsWith("/upsmon/", [StringComparison]::OrdinalIgnoreCase)) {
        $path = $path.Substring("/upsmon".Length)
    }

    $contentLength = 0
    if ($headers.ContainsKey("Content-Length")) {
        if ($headers["Content-Length"] -notmatch '^\d{1,5}$') {
            Send-HttpResponse $stream 400 "Bad Request" "text/plain" "Invalid content length."
            return
        }
        $contentLength = [int]$headers["Content-Length"]
    }
    if ($contentLength -gt $maximumRequestBody) {
        Send-HttpResponse $stream 413 "Payload Too Large" "text/plain" "Request is too large."
        return
    }

    $authorization = $headers["Authorization"]
    if ([string]::IsNullOrEmpty($authorization) -or
            -not $authorization.StartsWith("Basic ", [StringComparison]::OrdinalIgnoreCase)) {
        Send-HttpResponse $stream 401 "Unauthorized" "application/json" '{"error":"authentication_required"}'
        return
    }

    try {
        $credentialBytes = [Convert]::FromBase64String($authorization.Substring(6).Trim())
        $credentialText = [System.Text.Encoding]::UTF8.GetString($credentialBytes)
    } catch {
        Send-HttpResponse $stream 401 "Unauthorized" "application/json" '{"error":"invalid_credentials"}'
        return
    }
    $colon = $credentialText.IndexOf(':')
    if ($colon -le 0) {
        Send-HttpResponse $stream 401 "Unauthorized" "application/json" '{"error":"invalid_credentials"}'
        return
    }

    $username = $credentialText.Substring(0, $colon).ToLowerInvariant()
    $password = $credentialText.Substring($colon + 1)
    [Array]::Clear($credentialBytes, 0, $credentialBytes.Length)
    $recentAttempts = @(Get-RecentFailedAttempts $username)
    if ($recentAttempts.Count -ge 5) {
        Send-HttpResponse $stream 429 "Too Many Requests" `
            "application/json" '{"error":"too_many_attempts"}'
        return
    }
    $users = Read-UserState
    $user = $users | Where-Object { $_.Username -eq $username } | Select-Object -First 1
    if ($null -eq $user -or -not (Test-UserPassword $user $password)) {
        Record-FailedAttempt $username
        Send-HttpResponse $stream 401 "Unauthorized" "application/json" '{"error":"invalid_credentials"}'
        return
    }
    [void]$script:failedAuthAttempts.Remove($username)

    if ($method -eq "GET" -and $path -eq "/auth") {
        Send-HttpResponse $stream 204 "No Content" "application/json" "" $user.Role
        return
    }
    if ($method -eq "GET" -and $path -eq "/ups.txt") {
        $telemetryFile = Join-Path $UpsmonWebRoot "ups.txt"
        if (-not (Test-Path -LiteralPath $telemetryFile -PathType Leaf)) {
            Send-HttpResponse $stream 503 "Service Unavailable" `
                "application/json" '{"error":"telemetry_unavailable"}'
            return
        }
        $telemetry = [System.IO.File]::ReadAllText(
            $telemetryFile, [System.Text.Encoding]::UTF8)
        if ($telemetry.Length -gt 1024) {
            Send-HttpResponse $stream 413 "Payload Too Large" "text/plain" "Telemetry response is too large."
            return
        }
        Send-HttpResponse $stream 200 "OK" "text/plain" $telemetry
        return
    }
    if ($method -eq "GET" -and $path -eq "/events.csv") {
        $record = Get-ChildItem -LiteralPath $UpsmonDataDirectory `
            -Filter "EventRecord.CSV" -File -Recurse |
            Sort-Object LastWriteTimeUtc -Descending |
            Select-Object -First 1
        if ($null -eq $record) {
            Send-HttpResponse $stream 503 "Service Unavailable" `
                "application/json" '{"error":"event_log_unavailable"}'
            return
        }
        if ($record.Length -gt $maximumEventLog) {
            Send-HttpResponse $stream 413 "Payload Too Large" `
                "text/plain" "Event log is too large."
            return
        }
        $file = [System.IO.File]::Open(
            $record.FullName,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read,
            [System.IO.FileShare]::ReadWrite)
        try {
            $reader = New-Object System.IO.StreamReader(
                $file, [System.Text.Encoding]::GetEncoding(1251), $true)
            $eventText = $reader.ReadToEnd()
            $reader.Dispose()
        } finally {
            $file.Dispose()
        }
        if ([System.Text.Encoding]::UTF8.GetByteCount($eventText) -gt $maximumEventLog) {
            Send-HttpResponse $stream 413 "Payload Too Large" `
                "text/plain" "Event log is too large."
            return
        }
        Send-HttpResponse $stream 200 "OK" "text/csv" $eventText
        return
    }
    if ($method -eq "GET" -and $path -eq "/users") {
        if ($user.Role -ne "admin") {
            Send-HttpResponse $stream 403 "Forbidden" `
                "application/json" '{"error":"admin_required"}'
            return
        }
        $names = @($users | ForEach-Object { $_.Username } | Sort-Object)
        $responseJson = ConvertTo-Json -InputObject @{ users = $names } -Compress
        Send-HttpResponse $stream 200 "OK" "application/json" $responseJson
        return
    }
    if ($method -eq "POST" -and $path -eq "/users") {
        if ($user.Role -ne "admin") {
            Send-HttpResponse $stream 403 "Forbidden" `
                "application/json" '{"error":"admin_required"}'
            return
        }
        if ($contentLength -eq 0) {
            Send-HttpResponse $stream 400 "Bad Request" `
                "application/json" '{"error":"request_body_required"}'
            return
        }
        try {
            $body = Read-RequestBody $stream $contentLength | ConvertFrom-Json
        } catch {
            Send-HttpResponse $stream 400 "Bad Request" `
                "application/json" '{"error":"invalid_request"}'
            return
        }

        $newUsername = [string]$body.username
        if ($newUsername -notmatch '^[A-Za-z0-9._-]{3,32}$' -or
                $newUsername -ieq "admin") {
            Send-HttpResponse $stream 400 "Bad Request" `
                "application/json" '{"error":"invalid_username"}'
            return
        }
        $newUsername = $newUsername.ToLowerInvariant()
        $newPassword = [string]$body.password
        if ($newPassword.Length -lt 8) {
            Send-HttpResponse $stream 400 "Bad Request" `
                "application/json" '{"error":"password_too_short"}'
            return
        }
        if ($users | Where-Object { $_.Username -eq $newUsername }) {
            Send-HttpResponse $stream 409 "Conflict" `
                "application/json" '{"error":"username_exists"}'
            return
        }

        $newRecord = New-UserRecord $newUsername $newPassword
        $users = @($users) + @($newRecord)
        Write-UserState $users
        Send-HttpResponse $stream 201 "Created" `
            "application/json" ('{"username":"' + $newUsername + '"}')
        return
    }

    Send-HttpResponse $stream 404 "Not Found" "application/json" '{"error":"not_found"}'
}

$listener = New-Object System.Net.Sockets.TcpListener(
    [System.Net.IPAddress]::Loopback, $Port)
try {
    $listener.Start()
    Write-Output "Authenticated UPSMON gateway is listening on 127.0.0.1:$Port."
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            Handle-Client $client
        } catch {
            Write-Warning ("Request failed: {0}" -f $_.Exception.Message)
            try {
                Send-HttpResponse $client.GetStream() 500 "Internal Server Error" `
                    "application/json" '{"error":"internal_error"}'
            } catch {
                Write-Warning "Unable to send an error response to the client."
            }
        } finally {
            $client.Close()
        }
    }
} finally {
    $listener.Stop()
}
