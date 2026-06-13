param(
    [ValidateSet("create", "verify", "characterize")]
    [string]$Mode = "create",

    [string]$Path = "spin-up-snapshots/romania-y13.bsnp",
    [string]$OutDir = "",
    [double]$Years = 13,
    [int]$InitialBears = 5800,
    [int]$MapLength = 1000,
    [string]$MapSource = "reference-data/generated-maps/U2018_CLC2018_V2020_20u1-1000x1000.txt",
    [double]$Food = 0.0045,
    [long]$Seed = 20260609,

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
        $mainClass = Join-Path $cp "Bears\Experiments\Calibration\SnapshotTool.class"
        if (Test-Path $mainClass) { return $cp }
    }
    throw "Could not find compiled SnapshotTool.class. Build project first (IntelliJ Build Project), then rerun. Checked: $($candidates -join ', ')"
}

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$classPath = Get-ClassPath -ProjectDir $projectDir

$javaArgs = @(
    $Xmx,
    "-Dsnap.mode=$Mode",
    "-Dsnap.path=$Path",
    "-Dsnap.years=$Years",
    "-Dsnap.initialBears=$InitialBears",
    "-Dsnap.mapLength=$MapLength",
    "-Dsnap.mapSource=$MapSource",
    "-Dsnap.food=$Food",
    "-Dsnap.seed=$Seed",
    "-cp", $classPath,
    "Bears.Experiments.Calibration.SnapshotTool"
)

if ($OutDir -and $OutDir.Trim().Length -gt 0) {
    $javaArgs = @("-Dsnap.outDir=$OutDir") + $javaArgs
}

Write-Host "Snapshot tool mode: $Mode"
Write-Host "Project dir: $projectDir"
Write-Host "Snapshot path: $Path"
if ($Mode -eq "create") {
    Write-Host "Spin-up: $InitialBears bears, $Years years, food=$Food, seed=$Seed"
}

Push-Location $projectDir
try {
    & java @javaArgs
    if ($LASTEXITCODE -ne 0) {
        throw "SnapshotTool exited with code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
