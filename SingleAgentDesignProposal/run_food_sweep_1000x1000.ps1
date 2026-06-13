param(
    # Which Settings field to sweep. Default sweeps food regrowth (the carrying-
    # capacity lever). Set e.g. -Param BEAR_FOUNDING_AGE_MEAN to sweep the founding
    # age structure (the initial-transient lever) instead.
    [string]$Param = "FOOD_GROWN_PER_TICK",

    # Comma-separated values of -Param to test. Default brackets the calibrated
    # 0.005 food downward; override when sweeping a different field.
    [string]$Values = "0.005,0.004,0.003,0.0025,0.002,0.0015",

    # When sweeping a non-food field, optionally pin FOOD_GROWN_PER_TICK to this
    # value so the carrying-capacity lever is held fixed. Empty = leave at default.
    [string]$FixedFood = "",

    [int]$InitialBears = 5800,
    [int]$MapLength = 1000,
    [double]$Years = 35,
    [double]$TailYears = 8,
    [int]$Replicates = 1,
    [double]$TargetPop = 5800,

    [string]$MapSource = "reference-data/generated-maps/U2018_CLC2018_V2020_20u1-1000x1000.txt",
    [long]$Seed = 20260609,
    [int]$SampleEveryTicks = 730,
    [long]$PopCap = 30000,
    [string]$OutputRoot = "food-sweep-output",

    [string]$Xmx = "-Xmx8g"
)

$ErrorActionPreference = "Stop"

function Get-ClassPath {
    param([string]$ProjectDir)

    $candidates = @(
        (Join-Path $ProjectDir "out-test"),
        (Join-Path $ProjectDir "out\production\SingleAgentDesignProposal")
    )

    foreach ($cp in $candidates) {
        $mainClass = Join-Path $cp "Bears\Experiments\Calibration\FoodGrownSweep.class"
        if (Test-Path $mainClass) {
            return $cp
        }
    }

    throw "Could not find compiled FoodGrownSweep.class. Build project first (IntelliJ Build Project), then rerun. Checked: $($candidates -join ', ')"
}

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$classPath = Get-ClassPath -ProjectDir $projectDir

$javaArgs = @(
    $Xmx,
    "-Dsweep.param=$Param",
    "-Dsweep.values=$Values",
    "-Dsweep.initialBears=$InitialBears",
    "-Dsweep.mapLength=$MapLength",
    "-Dsweep.years=$Years",
    "-Dsweep.tailYears=$TailYears",
    "-Dsweep.replicates=$Replicates",
    "-Dsweep.targetPop=$TargetPop",
    "-Dsweep.mapSource=$MapSource",
    "-Dsweep.seed=$Seed",
    "-Dsweep.sampleEveryTicks=$SampleEveryTicks",
    "-Dsweep.popCap=$PopCap",
    "-Dsweep.output=$OutputRoot",
    "-cp", $classPath,
    "Bears.Experiments.Calibration.FoodGrownSweep"
)

if ($FixedFood -and $FixedFood.Trim().Length -gt 0) {
    $javaArgs = @("-Dsweep.fixedFood=$FixedFood") + $javaArgs
}

Write-Host "Running $Param sweep"
Write-Host "Project dir: $projectDir"
Write-Host "Classpath: $classPath"
Write-Host "Values: $Values"
if ($FixedFood -and $FixedFood.Trim().Length -gt 0) {
    Write-Host "FOOD_GROWN_PER_TICK pinned: $FixedFood"
}
Write-Host "Initial bears: $InitialBears  Years: $Years  Replicates: $Replicates"
Write-Host "Target population: $TargetPop"
Write-Host ""

Push-Location $projectDir
try {
    & java @javaArgs
    if ($LASTEXITCODE -ne 0) {
        throw "FoodGrownSweep exited with code $LASTEXITCODE"
    }

    $outDir = Join-Path $projectDir $OutputRoot
    if (Test-Path $outDir) {
        $latest = Get-ChildItem -Path $outDir -Directory -Filter "sweep-*" |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -ne $latest) {
            Write-Host ""
            Write-Host "Latest sweep output: $($latest.FullName)"
            $summary = Join-Path $latest.FullName "summary.csv"
            if (Test-Path $summary) {
                Write-Host ""
                Write-Host "summary.csv:"
                Import-Csv $summary | Format-Table -AutoSize |
                    Out-String -Width 4096 | Write-Host
            }
        }
    }
}
finally {
    Pop-Location
}
