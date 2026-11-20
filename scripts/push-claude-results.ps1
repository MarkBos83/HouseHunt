# Started by watch-claude-results.ps1 as soon as the Claude scheduled task writes claude-results.json.
# Merges it into
# homes.json, commits and pushes to main (which makes GitHub rebuild the website), then deletes it.
# Output is appended to data\push-claude-results.log.

# "Continue": git and Maven write harmless warnings to stderr, which "Stop" would turn into errors.
# Every command's exit code is checked instead.
$ErrorActionPreference = "Continue"
$repository = Split-Path $PSScriptRoot -Parent
$resultsFile = Join-Path $repository "claude-results.json"
$logFile = Join-Path $repository "data\push-claude-results.log"
$maximumAttempts = 3

function Write-Log([string] $message) {
    New-Item -ItemType Directory -Force -Path (Split-Path $logFile) | Out-Null
    Add-Content -Path $logFile -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $message"
}

function Invoke-Checked([string] $description, [scriptblock] $command) {
    $output = & $command 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "$description failed: $output" }
    return $output.Trim()
}

function Update-FromGitHub {
    Invoke-Checked "git pull" { git pull --ff-only --quiet origin main } | Out-Null
}

function Merge-Results {
    Invoke-Checked "merge" { mvn -q compile exec:java "-Dexec.args=import-claude-results claude-results.json" }
}

function Test-HomesChanged {
    git diff --quiet -- homes.json
    return $LASTEXITCODE -ne 0
}

function Push-Homes {
    Invoke-Checked "git commit" { git commit --quiet -m "Update Funda and Pararius homes" -- homes.json } | Out-Null
    git push --quiet origin HEAD:main 2>&1 | Out-Null
    return $LASTEXITCODE -eq 0
}

# Undoes the local commit and merge, so the next attempt starts from the newest main.
function Undo-LocalCommit {
    Invoke-Checked "git reset" { git reset --quiet --keep HEAD~1 } | Out-Null
    Invoke-Checked "git checkout" { git checkout --quiet -- homes.json } | Out-Null
}

function Publish-Results {
    for ($attempt = 1; $attempt -le $maximumAttempts; $attempt++) {
        Update-FromGitHub
        $summary = Merge-Results
        if (-not (Test-HomesChanged)) { return "No changes. $summary" }
        if (Push-Homes) { return "Pushed. $summary" }
        Undo-LocalCommit
        Start-Sleep -Seconds 5
    }
    throw "could not push after $maximumAttempts attempts"
}

if (-not (Test-Path $resultsFile)) { exit 0 }
Set-Location $repository
try {
    Write-Log (Publish-Results)
    Remove-Item $resultsFile -ErrorAction Stop
} catch {
    # Moved aside so the watcher does not retry it over and over; it stays there to look at.
    Move-Item $resultsFile (Join-Path $repository "claude-results.failed.json") -Force
    Write-Log "FAILED: $_ (results kept in claude-results.failed.json)"
    exit 1
}
