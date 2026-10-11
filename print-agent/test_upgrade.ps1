$ErrorActionPreference = 'Stop'
# Load only pure upgrade functions: do not execute the interactive installer or touch real tasks.
$tree = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'install.ps1'), [ref]$null, [ref]$null)
foreach ($definition in $tree.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -in @('Stop-AgentForUpgrade','Start-AgentAfterUpgrade','Invoke-AgentInstallation','Update-AgentInstallation','Update-AgentInstallationLocked') }, $true)) {
    . ([scriptblock]::Create($definition.Extent.Text))
}
function Stop-ScheduledTask { param($TaskName) $script:state = 'Ready'; $script:stops++ }
function Get-ScheduledTask { param($TaskName) return @{State = $script:state} }
function Start-ScheduledTask {
    param($TaskName)
    $script:starts++
    if ($script:failStart -and $script:starts -eq 1) { throw 'Injected activation failure' }
    $script:state = 'Running'
}
function Copy-Item {
    param($LiteralPath, $Destination, [switch]$Force)
    if ($script:failCopy -and $Destination -eq (Join-Path $script:installed 'alarm.py')) {
        $script:failCopy = $false
        throw 'Injected replacement failure'
    }
    Microsoft.PowerShell.Management\Copy-Item -LiteralPath $LiteralPath -Destination $Destination -Force:$Force
}
$root = Join-Path ([IO.Path]::GetTempPath()) ('gokul-upgrade-tests-' + [guid]::NewGuid().ToString())
try {
    $fresh = Join-Path $root 'fresh-installed'
    $script:rejectedFresh = $false
    Invoke-AgentInstallation $fresh {
        if (Test-Path (Join-Path $fresh 'config.local.json')) { throw 'Fixture must start without an installed profile.' }
        try {
            Invoke-AgentInstallation $fresh {
                Set-Content -LiteralPath (Join-Path $fresh 'key.dpapi') -Value 'wrong-key'
                throw 'Concurrent fresh setup entered its mutation block.'
            }
        } catch {
            if ($_.Exception.Message -notlike '*installation lock*') { throw }
            $script:rejectedFresh = $true
        }
        if (!$script:rejectedFresh -or (Test-Path (Join-Path $fresh 'key.dpapi'))) { throw 'Fresh setup was not excluded before mutation.' }
        Set-Content -LiteralPath (Join-Path $fresh 'config.local.json') -Value 'installed-profile'
    }
    # Recheck existence inside the lock: a queued/retried installer must observe the completed setup.
    Invoke-AgentInstallation $fresh {
        if (!(Test-Path (Join-Path $fresh 'config.local.json'))) { throw 'Retry did not observe completed installation.' }
    }
    try { Invoke-AgentInstallation $fresh { throw 'Injected fresh setup failure' } } catch {
        if ($_.Exception.Message -ne 'Injected fresh setup failure') { throw }
    }
    Invoke-AgentInstallation $fresh { Write-Host 'Windows fresh setup exclusion and failure release passed.' }
    foreach ($scenario in @('success','missing-source','activation','replacement')) {
        $script:installed = Join-Path $root "$scenario-installed"
        $source = Join-Path $root "$scenario-source"
        New-Item -ItemType Directory -Path $script:installed,$source -Force | Out-Null
        foreach ($name in @('agent.py','alarm.py','background.py','requirements.txt')) {
            Set-Content -LiteralPath (Join-Path $source $name) -Value "new-$name"
            # Simulate upgrading an older installation that did not have alarm.py yet.
            if ($name -ne 'alarm.py') { Set-Content -LiteralPath (Join-Path $script:installed $name) -Value "old-$name" }
        }
        Set-Content -LiteralPath (Join-Path $script:installed 'key.dpapi') -Value 'original-key'
        Set-Content -LiteralPath (Join-Path $script:installed 'journal.sqlite') -Value 'original-journal'
        if ($scenario -eq 'missing-source') { Remove-Item -LiteralPath (Join-Path $source 'alarm.py') }
        $script:state = 'Running'; $script:stops = 0; $script:starts = 0
        $script:failStart = $scenario -eq 'activation'; $script:failCopy = $scenario -eq 'replacement'
        $held = [IO.File]::Open((Join-Path $script:installed '.install.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        try {
            $rejected = $false
            try { Update-AgentInstallation $script:installed $source 'fixture-task' } catch {
                if ($_.Exception.Message -notlike '*installation lock*') { throw }
                $rejected = $true
            }
            if (!$rejected -or $script:stops -ne 0 -or $script:starts -ne 0) { throw 'Concurrent installer was not rejected before stopping the task.' }
            if ((Get-ChildItem -LiteralPath $script:installed -Directory -Filter '.upgrade-*').Count -ne 0) { throw 'Concurrent installer created staging files.' }
        } finally { $held.Dispose() }
        $failed = $false
        try { Update-AgentInstallation $script:installed $source 'fixture-task' } catch { $failed = $true }
        if ($failed -ne ($scenario -ne 'success')) { throw "Unexpected outcome: $scenario" }
        if ($script:state -ne 'Running') { throw "Previous task was not recovered: $scenario" }
        if ($scenario -eq 'missing-source' -and $script:stops -ne 0) { throw 'Staging failure stopped the existing task.' }
        foreach ($name in @('agent.py','background.py','requirements.txt')) {
            $prefix = if ($scenario -eq 'success') { 'new-' } else { 'old-' }
            if ((Get-Content -LiteralPath (Join-Path $script:installed $name) -Raw).Trim() -ne ($prefix + $name)) { throw "File not restored: $scenario / $name" }
        }
        if ($scenario -ne 'success' -and (Test-Path -LiteralPath (Join-Path $script:installed 'alarm.py'))) { throw 'Rollback left a file absent from the original version.' }
        if ((Get-Content -LiteralPath (Join-Path $script:installed 'key.dpapi') -Raw).Trim() -ne 'original-key' -or (Get-Content -LiteralPath (Join-Path $script:installed 'journal.sqlite') -Raw).Trim() -ne 'original-journal') { throw 'Upgrade touched credentials or the journal.' }
        # Success and every failure path release the lock for the next installer.
        $released = [IO.File]::Open((Join-Path $script:installed '.install.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $released.Dispose()
        Write-Host "Windows upgrade $scenario passed."
    }
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
