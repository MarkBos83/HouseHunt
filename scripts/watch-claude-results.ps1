# Started at logon by a Windows scheduled job. Waits until the Claude scheduled task writes
# claude-results.json into this folder, then runs push-claude-results.ps1 straight away.
# Nothing runs in between: the wait is a file-system notification, not a timer.

$repository = Split-Path $PSScriptRoot -Parent
$resultsFile = Join-Path $repository "claude-results.json"
$pushScript = Join-Path $PSScriptRoot "push-claude-results.ps1"
# The wait wakes up once a minute anyway, in case a notification was missed.
$safetyCheckMillis = 60000
# Gives the file a moment to be fully written before it is read.
$settleSeconds = 3

$watcher = New-Object System.IO.FileSystemWatcher $repository, "claude-results.json"
$watchedChanges = [System.IO.WatcherChangeTypes]::Created -bor [System.IO.WatcherChangeTypes]::Changed -bor [System.IO.WatcherChangeTypes]::Renamed

while ($true) {
    $watcher.WaitForChanged($watchedChanges, $safetyCheckMillis) | Out-Null
    if (Test-Path $resultsFile) {
        Start-Sleep -Seconds $settleSeconds
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $pushScript
    }
}
