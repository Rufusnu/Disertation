param(
    # Comma-separated FOOD_GROWN_PER_TICK values to test. Default brackets the
    # calibrated 0.005 downward, since the current equilibrium is far above 5800
    # and we need LESS food regrowth to bring carrying capacity down.
    [string]$Values = "0.005,0.004,0.003,0.0025,0.002,0.0015",

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

Write-Host "Running FOOD_GROWN_PER_TICK sweep"
Write-Host "Project dir: $projectDir"
Write-Host "Classpath: $classPath"
Write-Host "Values: $Values"
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
