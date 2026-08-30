param(
    [string]$Gateway = "https://localhost:8443",
    [string]$CertificatePassword = "changeit"
)

$ErrorActionPreference = "Stop"
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$certificatePath = Join-Path $projectRoot ".certs\partner-client.p12"
$fixtureRoot = Join-Path $projectRoot "ingestion-service\src\test\resources\fixtures"
$certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
    $certificatePath,
    $CertificatePassword,
    [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::DefaultKeySet)
$results = [ordered]@{}

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Get-AccessToken([string]$ClientId, [string]$ClientSecret, [string]$Scope) {
    $credentials = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${ClientId}:${ClientSecret}"))
    $response = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate -Method Post `
        -Uri "$Gateway/oauth2/token" -Headers @{ Authorization = "Basic $credentials" } `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{ grant_type = "client_credentials"; scope = $Scope }
    Assert-Condition ($response.access_token.Length -gt 100) "OAuth token response did not contain an access token."
    return $response.access_token
}

function Get-Headers([string]$Token) {
    return @{ Authorization = "Bearer $Token" }
}

function Send-Edi([string]$FixtureName, [string]$Token, [string]$SpoofedPartner = "") {
    $headers = Get-Headers $Token
    if ($SpoofedPartner) { $headers["X-Partner-Id"] = $SpoofedPartner }
    return Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate -Method Post `
        -Uri "$Gateway/api/v1/edi/upload" -Headers $headers `
        -Form @{ file = Get-Item (Join-Path $fixtureRoot $FixtureName) }
}

function Wait-EventStatus([string]$EventId, [string]$Token) {
    for ($attempt = 0; $attempt -lt 80; $attempt++) {
        try {
            return Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate -Method Get `
                -Uri "$Gateway/api/v1/edi/status/$EventId" -Headers (Get-Headers $Token)
        } catch {
            if ($null -ne $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) {
                Start-Sleep -Milliseconds 250
                continue
            }
            throw
        }
    }
    throw "Timed out waiting for event status $EventId."
}

function Wait-Until([scriptblock]$Condition, [string]$FailureMessage) {
    for ($attempt = 0; $attempt -lt 80; $attempt++) {
        if (& $Condition) { return }
        Start-Sleep -Milliseconds 250
    }
    throw $FailureMessage
}

function Assert-HttpFailure([scriptblock]$Action, [int]$ExpectedStatus, [string]$Message) {
    try {
        & $Action | Out-Null
        throw "$Message Request unexpectedly succeeded."
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $actual = [int]$_.Exception.Response.StatusCode
        if ($actual -ne $ExpectedStatus) { throw "$Message Expected HTTP $ExpectedStatus but received $actual." }
    }
}

function Test-ClosedPort([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connected = $client.ConnectAsync("127.0.0.1", $Port).Wait(750)
        return -not ($connected -and $client.Connected)
    } catch {
        return $true
    } finally {
        $client.Dispose()
    }
}

$partnerToken = Get-AccessToken "partner-client" "change-me-partner" "edi:read edi:write"
$readerToken = Get-AccessToken "reader-client" "change-me-reader" "edi:read"
$adminToken = Get-AccessToken "admin-client" "change-me-admin" "edi:read edi:write admin"
$results.OAuth = "partner, reader, and admin client_credentials tokens issued"

$noCertificateHandler = [System.Net.Http.HttpClientHandler]::new()
$noCertificateHandler.ServerCertificateCustomValidationCallback = { $true }
$noCertificateClient = [System.Net.Http.HttpClient]::new($noCertificateHandler)
try {
    $noCertificateClient.GetAsync("$Gateway/actuator/health").GetAwaiter().GetResult() | Out-Null
    throw "Gateway accepted a TLS connection without a client certificate."
} catch {
    if ($_.Exception.Message -eq "Gateway accepted a TLS connection without a client certificate.") { throw }
    $results.Mtls = "connection without client certificate rejected during TLS"
} finally {
    $noCertificateClient.Dispose()
    $noCertificateHandler.Dispose()
}

Assert-HttpFailure {
    Invoke-WebRequest -SkipCertificateCheck -Certificate $certificate -Uri "$Gateway/api/v1/orders"
} 401 "Missing bearer token."
Assert-HttpFailure {
    Send-Edi "valid-850.edi" $readerToken
} 403 "Read-only upload."
$results.Authorization = "missing token=401 and read-only upload=403"

Assert-Condition (Test-ClosedPort 8081) "Ingestion service is reachable directly on host port 8081."
Assert-Condition (Test-ClosedPort 8082) "Order service is reachable directly on host port 8082."
$results.ServiceIsolation = "ports 8081 and 8082 are not host-accessible"

$accepted = Send-Edi "valid-850.edi" $partnerToken "SPOOFED-PARTNER"
Assert-Condition ($accepted.status -eq "QUEUED") "Valid 850 did not return QUEUED."
Assert-Condition ($accepted.documentType -eq "PO_850") "Valid 850 response has the wrong document type."
$validStatus = Wait-EventStatus $accepted.eventId $partnerToken
Assert-Condition ($validStatus.status -eq "PROCESSED") "Valid 850 did not reach PROCESSED."

$orders = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/orders?size=100" -Headers (Get-Headers $readerToken)
$persistedOrder = @($orders.content | Where-Object { $_.poNumber -eq "PO-100245" -and $_.partnerCode -eq "SENDERID" })
Assert-Condition ($persistedOrder.Count -eq 1) "Expected exactly one SENDERID PO-100245 order."
Assert-Condition ([decimal]$persistedOrder[0].totalAmount -eq [decimal]1687.50) "Stored PO total is not 1687.50."
Assert-Condition ($persistedOrder[0].status -eq "PERSISTED") "Valid PO is not PERSISTED."
$orderDetail = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/orders/$($persistedOrder[0].id)" -Headers (Get-Headers $readerToken)
Assert-Condition ($orderDetail.lines.Count -eq 2) "Valid PO detail does not contain two lines."
Assert-Condition ($orderDetail.partnerCode -eq "SENDERID") "Spoofed partner header reached the order service."
$results.Valid850 = "202/QUEUED -> PROCESSED; one PERSISTED order; total=1687.50; 2 lines; spoof stripped"

$duplicate = Send-Edi "duplicate-850.edi" $partnerToken
$duplicateStatus = Wait-EventStatus $duplicate.eventId $partnerToken
Assert-Condition ($duplicateStatus.status -eq "DUPLICATE") "Duplicate 850 did not reach DUPLICATE."
$ordersAfterDuplicate = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/orders?size=100" -Headers (Get-Headers $readerToken)
Assert-Condition (@($ordersAfterDuplicate.content | Where-Object { $_.poNumber -eq "PO-100245" -and $_.partnerCode -eq "SENDERID" }).Count -eq 1) `
    "Duplicate 850 created a second purchase order."
$results.Duplicate = "second upload status=DUPLICATE; still exactly one SENDERID order"

$lineMismatch = Send-Edi "line-count-mismatch-850.edi" $partnerToken
$lineMismatchStatus = Wait-EventStatus $lineMismatch.eventId $partnerToken
Assert-Condition ($lineMismatchStatus.status -eq "REJECTED") "CTT01 mismatch was not rejected."
$lineMismatchOrders = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/orders?status=REJECTED&size=100" -Headers (Get-Headers $readerToken)
$lineMismatchOrder = @($lineMismatchOrders.content | Where-Object { $_.poNumber -eq "PO-LINE-MISMATCH" })
Assert-Condition ($lineMismatchOrder.Count -eq 1) "Rejected line-count order is missing."
$lineMismatchDetail = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/orders/$($lineMismatchOrder[0].id)" -Headers (Get-Headers $readerToken)
Assert-Condition (@($lineMismatchDetail.history | Where-Object { $_.reason -like "LINE_COUNT_MISMATCH:*" }).Count -gt 0) `
    "Line-count rejection history lacks LINE_COUNT_MISMATCH."
$results.R2 = "CTT01/PO1 mismatch rejected with LINE_COUNT_MISMATCH audit reason"

$zeroQuantity = Send-Edi "zero-quantity-850.edi" $partnerToken
$zeroQuantityStatus = Wait-EventStatus $zeroQuantity.eventId $partnerToken
Assert-Condition ($zeroQuantityStatus.status -eq "REJECTED") "Zero-quantity PO was not rejected."
$results.R4 = "zero quantity rejected"

$unknownPartner = Send-Edi "valid-850.edi" $adminToken
$unknownStatus = Wait-EventStatus $unknownPartner.eventId $adminToken
Assert-Condition ($unknownStatus.status -eq "RECEIVED") "Unknown partner event was not held."
$partners = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/partners" -Headers (Get-Headers $adminToken)
$pendingPartner = @($partners | Where-Object { $_.code -eq "admin-client" })
Assert-Condition ($pendingPartner.Count -eq 1 -and $pendingPartner[0].status -eq "PENDING") `
    "Unknown OAuth partner was not auto-created as PENDING."
$results.R1 = "unknown partner auto-created PENDING and event held in RECEIVED/VALIDATING"

$matchingInvoice = Send-Edi "valid-810.edi" $partnerToken
$matchingStatus = Wait-EventStatus $matchingInvoice.eventId $partnerToken
Assert-Condition ($matchingStatus.status -eq "PROCESSED") "Matching invoice event did not process."
$mismatchedInvoice = Send-Edi "mismatched-810.edi" $partnerToken
$mismatchedStatus = Wait-EventStatus $mismatchedInvoice.eventId $partnerToken
Assert-Condition ($mismatchedStatus.status -eq "PROCESSED") "Mismatched invoice event did not complete."
$invoices = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/invoices?size=100" -Headers (Get-Headers $readerToken)
Assert-Condition (@($invoices.content | Where-Object { $_.invoiceNumber -eq "INV-2001" -and $_.status -eq "PERSISTED" }).Count -eq 1) `
    "Matching invoice was not PERSISTED."
Assert-Condition (@($invoices.content | Where-Object { $_.invoiceNumber -eq "INV-2002" -and $_.status -eq "MATCH_EXCEPTION" }).Count -eq 1) `
    "Mismatched invoice was not MATCH_EXCEPTION."
$results.R5 = "matching invoice=PERSISTED; mismatched invoice=MATCH_EXCEPTION"

$zeroFilterId = [guid]::NewGuid()
$filtered = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/orders?partnerId=$zeroFilterId&status=PERSISTED&from=2035-01-01&to=2035-01-02" `
    -Headers (Get-Headers $readerToken)
Assert-Condition ($filtered.totalElements -eq 0) "Order filters returned rows for an impossible query."
$results.Filtering = "partner/status/date zero-result filter passed"

try {
    Send-Edi "malformed-850.edi" $partnerToken | Out-Null
    throw "Malformed 850 unexpectedly succeeded."
} catch {
    if ($null -eq $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 422) { throw }
    Assert-Condition ($_.ErrorDetails.Message -like "*segment count*") "Malformed 422 reason is not specific."
}

$malformedDeadLetter = $null
Wait-Until {
    $page = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
        -Uri "$Gateway/api/v1/dead-letters?size=100" -Headers (Get-Headers $adminToken)
    $script:malformedDeadLetter = @($page.content | Where-Object { $_.errorReason -like "*segment count*" } | Select-Object -First 1)
    return $script:malformedDeadLetter.Count -eq 1
} "Malformed upload did not create a persisted dead-letter record."
$retry = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate -Method Post `
    -Uri "$Gateway/api/v1/dead-letters/$($malformedDeadLetter[0].id)/retry" -Headers (Get-Headers $adminToken)
Assert-Condition ($retry.status -eq "RETRIED" -and $retry.retryCount -eq 1) "Dead-letter retry did not update and republish."
$results.DLQ = "malformed=422 with segment-count reason; persisted DLQ; retry status=RETRIED"

$audit = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/audit?query=LINE_COUNT_MISMATCH&size=100" -Headers (Get-Headers $adminToken)
Assert-Condition ($audit.totalElements -gt 0) "Audit search did not find LINE_COUNT_MISMATCH."
$results.Audit = "searchable state-transition history present"

$metrics = Invoke-RestMethod -SkipCertificateCheck -Certificate $certificate `
    -Uri "$Gateway/api/v1/metrics/summary" -Headers (Get-Headers $readerToken)
Assert-Condition ($metrics.deadLetterDepth -ge 1) "Metrics summary did not reflect open dead letters."
$results.MetricsSummary = "kafkaLag=$($metrics.kafkaLag), deadLetterDepth=$($metrics.deadLetterDepth)"

$dashboard = Invoke-WebRequest -Uri "http://localhost:5173"
Assert-Condition ($dashboard.StatusCode -eq 200 -and $dashboard.Content -like "*EDIFlow*") "Dashboard did not serve the EDIFlow application."
$prometheus = Invoke-WebRequest -Uri "http://localhost:9090/-/ready"
Assert-Condition ($prometheus.StatusCode -eq 200) "Prometheus is not ready."
$grafana = Invoke-RestMethod -Uri "http://localhost:3000/api/health"
Assert-Condition ($grafana.database -eq "ok") "Grafana health check failed."
$results.OperationsUi = "dashboard=200, Prometheus=ready, Grafana database=ok"

$rateHandler = [System.Net.Http.HttpClientHandler]::new()
$rateHandler.ClientCertificates.Add($certificate)
$rateHandler.ServerCertificateCustomValidationCallback = { $true }
$rateClient = [System.Net.Http.HttpClient]::new($rateHandler)
$rateClient.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $readerToken)
try {
    $rateTasks = 1..30 | ForEach-Object { $rateClient.GetAsync("$Gateway/api/v1/orders?size=1") }
    $rateResponses = [System.Threading.Tasks.Task]::WhenAll($rateTasks).GetAwaiter().GetResult()
    $rateLimited = @($rateResponses | Where-Object { [int]$_.StatusCode -eq 429 }).Count
    Assert-Condition ($rateLimited -gt 0) "Concurrent burst did not trigger HTTP 429."
    $results.RateLimit = "$rateLimited of 30 concurrent requests returned 429"
} finally {
    $rateClient.Dispose()
    $rateHandler.Dispose()
}

[pscustomobject]$results | ConvertTo-Json -Depth 4
