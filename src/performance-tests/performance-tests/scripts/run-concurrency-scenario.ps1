param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("1", "2", "3", "4", "5", "6", "7")]
    [string]$Scenario,

    [string]$BaseUrl = "http://localhost:8080",
    [string]$P95Milliseconds = "",
    [string]$DatabaseContainer = "gudit-performance-postgres",
    [string]$Database = "gudit",
    [string]$DatabaseUser = "postgres",
    [string]$RedisContainer = "gudit-performance-redis",
    [int]$RedisDatabase = 0
)

$ErrorActionPreference = "Stop"
if ($RedisDatabase -lt 0) { throw "RedisDatabase cannot be negative." }

$suiteRoot = Split-Path $PSScriptRoot -Parent

$prepareScript = Join-Path `
    $suiteRoot `
    "test-data/prepare-db.ps1"

$scenarioScripts = @{
    "1" = "01-oversell-hotspot.js"
    "2" = "02-single-row-lock-capacity.js"
    "3" = "03-distributed-baseline.js"
    "4" = "04-duplicate-purchase-race.js"
    "5" = "05-cancel-race.js"
    "6" = "06-payment-confirm-race.js"
    "7" = "07-payment-confirm-cancel-race.js"
}

$scenarioFile = $scenarioScripts[$Scenario]

$scenarioPath = Join-Path `
    $suiteRoot `
    "k6/concurrency/$scenarioFile"

Write-Host (
    "[1/2] Preparing performance fixture: " +
    "scenario=$Scenario"
)

& $prepareScript `
    -ResetPerformanceDatabase `
    -Scenario $Scenario `
    -Container $DatabaseContainer `
    -Database $Database `
    -DatabaseUser $DatabaseUser `
    -RedisContainer $RedisContainer

Write-Host (
    "[2/2] Running k6 scenario: " +
    "$scenarioFile"
)

$k6Arguments = @(
    "run",
    "-e",
    "BASE_URL=$BaseUrl"
)

if ($P95Milliseconds) {
    $k6Arguments += @(
        "-e",
        "P95_MS=$P95Milliseconds"
    )
}

$k6Arguments += $scenarioPath

& k6 @k6Arguments

if ($LASTEXITCODE -ne 0) {
    throw "k6 scenario $Scenario failed."
}

function Wait-RedisStock {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Key,

        [Parameter(Mandatory = $true)]
        [string]$ExpectedValue,

        [int]$TimeoutSeconds = 30,
        [int]$PollMilliseconds = 500
    )

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastValue = $null

    do {
        $redisValue = & docker exec `
            $RedisContainer `
            redis-cli `
            -n $RedisDatabase `
            GET `
            $Key

        if ($LASTEXITCODE -ne 0) {
            throw "Redis stock lookup failed: key=$Key"
        }

        $lastValue = if ($null -eq $redisValue) {
            $null
        }
        else {
            "$redisValue".Trim()
        }

        if ($lastValue -eq $ExpectedValue) {
            return $lastValue
        }

        Start-Sleep -Milliseconds $PollMilliseconds
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    return $lastValue
}

if ($Scenario -eq "6") {
    Write-Host "Verifying payment fixture"

    $paymentState = & docker exec `
        $DatabaseContainer `
        psql `
        -U $DatabaseUser `
        -d $Database `
        -t `
        -A `
        -F "|" `
        -c "SELECT status, payment_key FROM payments WHERE id = 2;"

    if ($LASTEXITCODE -ne 0) {
        throw "Payment state verification query failed."
    }

    $paymentState = $paymentState.Trim()

    if ($paymentState -ne "DONE|PERF_PAYMENT_KEY_0002") {
        throw (
            "Payment state verification failed. " +
            "Expected DONE|PERF_PAYMENT_KEY_0002, " +
            "actual=$paymentState"
        )
    }

    Write-Host (
        "Payment fixture verified: " +
        $paymentState
    )
}

if ($Scenario -eq "7") {
    Write-Host "Verifying payment-confirm-cancel race fixture"

    $databaseState = & docker exec `
        $DatabaseContainer `
        psql `
        -U $DatabaseUser `
        -d $Database `
        -t `
        -A `
        -F "|" `
        -c @"
SELECT
    p.status,
    pay.status
FROM purchases p
JOIN payments pay
    ON pay.purchase_id = p.id
WHERE p.id = 3;
"@

    if ($LASTEXITCODE -ne 0) {
        throw "Payment-confirm-cancel database verification query failed."
    }

    $databaseState = $databaseState.Trim()

    if ($databaseState -eq "PURCHASED|DONE") {
        $redisStock = Wait-RedisStock `
            -Key "sale:106:stock" `
            -ExpectedValue "99"

        $isConsistent = $redisStock -eq "99"
    }
    elseif ($databaseState -eq "CANCELED|CANCELED") {
        $redisStock = Wait-RedisStock `
            -Key "sale:106:stock" `
            -ExpectedValue "100"

        $isConsistent = $redisStock -eq "100"
    }
    else {
        $redisStock = "unknown"
        $isConsistent = $false
    }

    if (-not $isConsistent) {
        throw (
            "Payment-confirm-cancel final state is inconsistent. " +
            "database=$databaseState, redisStock=$redisStock"
        )
    }

    Write-Host (
        "Payment-confirm-cancel fixture verified: " +
        "database=$databaseState, redisStock=$redisStock"
    )
}

Write-Host "k6 scenario $Scenario passed."
