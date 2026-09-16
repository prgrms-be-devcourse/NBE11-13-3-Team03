param(
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
$k6Directory = Join-Path $suiteRoot "k6"
$prepareDbPath = Join-Path $suiteRoot "test-data/prepare-db.ps1"
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$resultDirectory = Join-Path $suiteRoot "results/concurrency-$runId"
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

$preflight = Join-Path $k6Directory "preflight.js"
& k6 run -e "BASE_URL=$BaseUrl" $preflight
if ($LASTEXITCODE -ne 0) {
    throw "Preflight failed. Reset the fixture data and verify the Spring application before running load tests."
}

$scenarios = @(
    "01-oversell-hotspot.js",
    "02-single-row-lock-capacity.js",
    "03-distributed-baseline.js",
    "04-duplicate-purchase-race.js",
    "05-cancel-race.js"
)

$isolatedScenarios = @(
    @{
        Number = "6"
        File = "06-payment-confirm-race.js"
    },
    @{
        Number = "7"
        File = "07-payment-confirm-cancel-race.js"
    }
)

function Reset-ConcurrencyState {
    Write-Host "전체 동시성 테스트 종료 후 성능테스트 Redis와 PostgreSQL을 기준 상태로 초기화합니다."
    & docker exec $RedisContainer redis-cli -n $RedisDatabase FLUSHDB SYNC | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "성능테스트 Redis 초기화에 실패했습니다." }

    & $prepareDbPath `
        -ResetPerformanceDatabase `
        -Container $DatabaseContainer `
        -Database $Database `
        -DatabaseUser $DatabaseUser
    if ($LASTEXITCODE -ne 0) { throw "성능테스트 DB 초기화에 실패했습니다." }
}

function Prepare-IsolatedScenario {
    param([string]$Scenario)

    & $prepareDbPath `
        -ResetPerformanceDatabase `
        -Scenario $Scenario `
        -Container $DatabaseContainer `
        -Database $Database `
        -DatabaseUser $DatabaseUser `
        -RedisContainer $RedisContainer
    if ($LASTEXITCODE -ne 0) {
        throw "시나리오 $Scenario 기준 상태 초기화에 실패했습니다."
    }
}

function Verify-PaymentConfirmRace {
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
        throw "시나리오 6 결제 상태 검증 쿼리에 실패했습니다."
    }

    $paymentState = $paymentState.Trim()
    if ($paymentState -ne "DONE|PERF_PAYMENT_KEY_0002") {
        throw "시나리오 6 결제 상태가 올바르지 않습니다: $paymentState"
    }
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
            throw "Redis 재고 조회에 실패했습니다: key=$Key"
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

function Verify-PaymentConfirmCancelRace {
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
        throw "시나리오 7 DB 상태 검증 쿼리에 실패했습니다."
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
            "시나리오 7 최종 상태가 일관되지 않습니다: " +
            "database=$databaseState, redisStock=$redisStock"
        )
    }

    Write-Host (
        "시나리오 7 최종 상태 검증 완료: " +
        "database=$databaseState, redisStock=$redisStock"
    )
}

$failed = @()
$finalResetError = $null
try {
    foreach ($scenario in $scenarios) {
        $scriptPath = Join-Path $k6Directory "concurrency/$scenario"
        $summaryPath = Join-Path $resultDirectory ($scenario -replace "\.js$", ".summary.json")
        $arguments = @("run", "-e", "BASE_URL=$BaseUrl")
        if ($P95Milliseconds) {
            $arguments += @("-e", "P95_MS=$P95Milliseconds")
        }
        $arguments += @("--summary-export", $summaryPath, $scriptPath)
        & k6 @arguments
        if ($LASTEXITCODE -ne 0) {
            $failed += $scenario
        }
    }

    foreach ($scenario in $isolatedScenarios) {
        Prepare-IsolatedScenario -Scenario $scenario.Number

        $scriptPath = Join-Path $k6Directory "concurrency/$($scenario.File)"
        $summaryPath = Join-Path `
            $resultDirectory `
            ($scenario.File -replace "\.js$", ".summary.json")
        $arguments = @("run", "-e", "BASE_URL=$BaseUrl")
        if ($P95Milliseconds) {
            $arguments += @("-e", "P95_MS=$P95Milliseconds")
        }
        $arguments += @("--summary-export", $summaryPath, $scriptPath)

        & k6 @arguments
        if ($LASTEXITCODE -ne 0) {
            $failed += $scenario.File
        }

        try {
            if ($scenario.Number -eq "6") {
                Verify-PaymentConfirmRace
            }
            else {
                Verify-PaymentConfirmCancelRace
            }
        }
        catch {
            $failed += "$($scenario.File) postcondition"
            Write-Error $_
        }
    }
}
finally {
    try {
        Reset-ConcurrencyState
    }
    catch {
        $finalResetError = $_
    }
}

Write-Host "Summaries: $resultDirectory"
if ($null -ne $finalResetError) {
    throw "동시성 테스트 종료 후 기준 상태 복원에 실패했습니다: $($finalResetError.Exception.Message)"
}
if ($failed.Count -gt 0) {
    throw "Failed scenarios: $($failed -join ', ')"
}

Write-Host "All concurrency scenarios passed."
