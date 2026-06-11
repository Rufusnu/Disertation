param(
    [string]$OutputRoot = "calibration-output",
    [string]$SweepDir,
    [int]$ProjectedEvalCount = 250,
    [double]$SafetyFactor = 1.25
)

$ErrorActionPreference = "Stop"

function Format-Duration {
    param([double]$Seconds)

    if ($Seconds -lt 60) {
        return ("{0:N1}s" -f $Seconds)
    }

    $ts = [TimeSpan]::FromSeconds([math]::Round($Seconds))
    if ($ts.TotalHours -ge 1) {
        return ("{0:%h}h {0:%m}m {0:%s}s" -f $ts)
    }
    return ("{0:%m}m {0:%s}s" -f $ts)
}

function Get-SweepDir {
    param(
        [string]$ProjectDir,
        [string]$OutRoot,
        [string]$ExplicitSweep
    )

    if ($ExplicitSweep -and $ExplicitSweep.Trim().Length -gt 0) {
        if (-not (Test-Path $ExplicitSweep)) {
            throw "Provided -SweepDir does not exist: $ExplicitSweep"
        }
        return (Resolve-Path $ExplicitSweep).Path
    }

    $rootCandidates = @()
    $probe = $ProjectDir
    for ($i = 0; $i -lt 4; $i++) {
        $rootCandidates += (Join-Path $probe $OutRoot)
        $parent = Split-Path -Parent $probe
        if (-not $parent -or $parent -eq $probe) {
            break
        }
        $probe = $parent
    }

    $root = $null
    foreach ($candidate in $rootCandidates) {
        if (Test-Path $candidate) {
            $root = $candidate
            break
        }
    }

    if ($null -eq $root) {
        throw "Output root not found. Checked: $($rootCandidates -join ', ')"
    }

    $latest = Get-ChildItem -Path $root -Directory -Filter "sweep-*" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $latest) {
        throw "No sweep-* directory found in: $root"
    }

    return $latest.FullName
}

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$selectedSweep = Get-SweepDir -ProjectDir $projectDir -OutRoot $OutputRoot -ExplicitSweep $SweepDir
$pointsPath = Join-Path $selectedSweep "points.csv"

if (-not (Test-Path $pointsPath)) {
    throw "points.csv not found in sweep folder: $selectedSweep"
}

$rows = Import-Csv $pointsPath
if ($null -eq $rows -or $rows.Count -eq 0) {
    throw "points.csv is empty: $pointsPath"
}

$elapsedMs = @()
$replicateCounts = @()

foreach ($r in $rows) {
    $ms = 0.0
    $rep = 0
    [void][double]::TryParse(($r.elapsedMillis -as [string]), [ref]$ms)
    [void][int]::TryParse(($r.replicateCount -as [string]), [ref]$rep)
    $elapsedMs += $ms
    $replicateCounts += $rep
}

$evalCount = $rows.Count
$totalMs = ($elapsedMs | Measure-Object -Sum).Sum
$avgMs = ($elapsedMs | Measure-Object -Average).Average
$minMs = ($elapsedMs | Measure-Object -Minimum).Minimum
$maxMs = ($elapsedMs | Measure-Object -Maximum).Maximum
$sortedMs = $elapsedMs | Sort-Object
$medianMs = if ($sortedMs.Count % 2 -eq 1) {
    $sortedMs[[int]($sortedMs.Count / 2)]
} else {
    ($sortedMs[[int]($sortedMs.Count / 2) - 1] + $sortedMs[[int]($sortedMs.Count / 2)]) / 2.0
}

$uniqueReplicates = ($replicateCounts | Select-Object -Unique | Sort-Object)
$meanReplicates = ($replicateCounts | Measure-Object -Average).Average
$estimatedReplicateRuns = [math]::Round($evalCount * $meanReplicates, 2)

$projectedSec = ($avgMs / 1000.0) * $ProjectedEvalCount
$projectedSecSafe = $projectedSec * $SafetyFactor

Write-Host "Sweep folder: $selectedSweep"
Write-Host "Points file : $pointsPath"
Write-Host ""
Write-Host "Observed metrics"
Write-Host "- candidate evaluations: $evalCount"
Write-Host "- replicateCount values: $($uniqueReplicates -join ', ')"
Write-Host "- estimated replicate runs: $estimatedReplicateRuns"
Write-Host "- total elapsed (sum rows): $(Format-Duration ($totalMs / 1000.0))"
Write-Host "- avg elapsed/eval: $(Format-Duration ($avgMs / 1000.0))"
Write-Host "- median elapsed/eval: $(Format-Duration ($medianMs / 1000.0))"
Write-Host "- min elapsed/eval: $(Format-Duration ($minMs / 1000.0))"
Write-Host "- max elapsed/eval: $(Format-Duration ($maxMs / 1000.0))"
Write-Host ""
Write-Host "Projection"
Write-Host "- projected evaluations: $ProjectedEvalCount"
Write-Host "- projected wall-time: $(Format-Duration $projectedSec)"
Write-Host "- projected wall-time (+ safety factor x$SafetyFactor): $(Format-Duration $projectedSecSafe)"
