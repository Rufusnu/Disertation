param(
    [ValidateSet("pilot", "full")]
    [string]$Mode = "pilot",

    [int]$InitialBears = 5800,
    [int]$MapLength = 1000,
    [double]$KTarget = 5800,
    [long]$Seed = 20260609,

    [string]$MapSource = "reference-data/generated-maps/U2018_CLC2018_V2020_20u1-1000x1000.txt",
    [string]$OutputRoot = "calibration-output",

    [switch]$ResumeLatest,
    [string]$ResumeDir,

    [switch]$VerboseCalibration
)

$ErrorActionPreference = "Stop"

function Get-ClassPath {
    param([string]$ProjectDir)

    $candidates = @(
        (Join-Path $ProjectDir "out-test"),
        (Join-Path $ProjectDir "out\production\SingleAgentDesignProposal")
    )

    foreach ($cp in $candidates) {
        $mainClass = Join-Path $cp "Bears\Experiments\Calibration\CalibrationRunner.class"
        if (Test-Path $mainClass) {
            return $cp
        }
    }

    throw "Could not find compiled CalibrationRunner.class. Build project first (IntelliJ Build Project), then rerun. Checked: $($candidates -join ', ')"
}

function Get-ModeConfig {
    param([string]$SelectedMode)

    if ($SelectedMode -eq "full") {
        return @{
            BurnInYears  = 2
            EvalEndYears = 18
            Replicates   = 3
            LhsPoints    = 48
            RefineTop    = 4
            NmIterations = 30
            Xmx          = "-Xmx8g"
        }
    }

    if ($SelectedMode -eq "medium") {
        return @{
            BurnInYears  = 2
            EvalEndYears = 18
            Replicates   = 2      # was 3 — saves 33% time, still robust
            LhsPoints    = 36     # was 48 — still 6x dimensions
            RefineTop    = 3      # was 4
            NmIterations = 20     # was 30
            Xmx          = "-Xmx8g"
        }
    }

    # return @{
    #     BurnInYears  = 10
    #     EvalEndYears = 35
    #     Replicates   = 1 # Set to 2 for quick testing, but 1 is enough for a pilot run and keeps runtime very short.
    #     LhsPoints    = 10
    #     RefineTop    = 2
    #     NmIterations = 8
    #     Xmx          = "-Xmx6g"
    # }

    return @{
        BurnInYears  = 2
        EvalEndYears = 18
        Replicates   = 1 # Set to 2 for quick testing, but 1 is enough for a pilot run and keeps runtime very short.
        LhsPoints    = 14
        RefineTop    = 2
        NmIterations = 10
        Xmx          = "-Xmx6g"
    }
}

function Get-LatestSweepDir {
    param(
        [string]$ProjectDir,
        [string]$OutRoot
    )

    $root = Join-Path $ProjectDir $OutRoot
    if (-not (Test-Path $root)) {
        throw "Output root not found for resume: $root"
    }

    $latest = Get-ChildItem -Path $root -Directory -Filter "sweep-*" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $latest) {
        throw "No sweep-* directory found under: $root"
    }
    return $latest.FullName
}

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$classPath = Get-ClassPath -ProjectDir $projectDir
$cfg = Get-ModeConfig -SelectedMode $Mode
$verbose = if ($VerboseCalibration.IsPresent) { "true" } else { "false" }

$resumePath = $null
if ($ResumeDir -and $ResumeDir.Trim().Length -gt 0) {
    $resumePath = (Resolve-Path $ResumeDir).Path
} elseif ($ResumeLatest.IsPresent) {
    $resumePath = Get-LatestSweepDir -ProjectDir $projectDir -OutRoot $OutputRoot
}

if ($null -ne $resumePath) {
    $resumePoints = Join-Path $resumePath "points.csv"
    if (-not (Test-Path $resumePoints)) {
        throw "Resume directory does not contain points.csv: $resumePath"
    }
}

$javaArgs = @(
    $cfg.Xmx,
    "-Dcalib.initialBears=$InitialBears",
    "-Dcalib.mapLength=$MapLength",
    "-Dcalib.mapSource=$MapSource",
    "-Dcalib.kTarget=$KTarget",
    "-Dcalib.burnInYears=$($cfg.BurnInYears)",
    "-Dcalib.evalEndYears=$($cfg.EvalEndYears)",
    "-Dcalib.replicates=$($cfg.Replicates)",
    "-Dcalib.lhsPoints=$($cfg.LhsPoints)",
    "-Dcalib.refineTop=$($cfg.RefineTop)",
    "-Dcalib.nmIterations=$($cfg.NmIterations)",
    "-Dcalib.seed=$Seed",
    "-Dcalib.output=$OutputRoot",
    "-Dcalib.verbose=$verbose",
    "-cp", $classPath,
    "Bears.Experiments.Calibration.CalibrationRunner"
)

if ($null -ne $resumePath) {
    $javaArgs = @("-Dcalib.resumeFrom=$resumePath") + $javaArgs
}

Write-Host "Running calibration mode: $Mode"
Write-Host "Project dir: $projectDir"
Write-Host "Classpath: $classPath"
Write-Host "Map source: $MapSource"
Write-Host "Initial bears: $InitialBears"
Write-Host "K target: $KTarget"
if ($null -ne $resumePath) {
    Write-Host "Resume from: $resumePath"
}

Push-Location $projectDir
try {
    & java @javaArgs
    if ($LASTEXITCODE -ne 0) {
        throw "CalibrationRunner exited with code $LASTEXITCODE"
    }

    $outDir = Join-Path $projectDir $OutputRoot
    $latest = $null
    if ($null -ne $resumePath) {
        $latest = Get-Item $resumePath
        Write-Host "Latest sweep output: $($latest.FullName)"
    }
    elseif (Test-Path $outDir) {
        $latest = Get-ChildItem -Path $outDir -Directory -Filter "sweep-*" |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -ne $latest) {
            Write-Host "Latest sweep output: $($latest.FullName)"
        }
    }

    $estimatorScript = Join-Path $projectDir "estimate_calibration_runtime.ps1"
    if (Test-Path $estimatorScript) {
        Write-Host ""
        Write-Host "Runtime summary (from estimator):"
        $estimatorArgs = @(
            "-ExecutionPolicy", "Bypass",
            "-NoProfile",
            "-File", $estimatorScript,
            "-OutputRoot", $OutputRoot
        )
        if ($null -ne $latest) {
            $estimatorArgs += @("-SweepDir", $latest.FullName)
        }
        & powershell @estimatorArgs
    }
}
finally {
    Pop-Location
}
