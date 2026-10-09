# Run once as the shop's Windows account. No secret is sent to the website or command line.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Security
try {
    $picker = New-Object System.Windows.Forms.OpenFileDialog
    $picker.Title = 'Select the printer profile downloaded from Admin setup'
    $picker.Filter = 'Printer profile (config.local.json)|config.local.json'
    if ($picker.ShowDialog() -ne 'OK') { exit 0 }
    $profile = Get-Content -LiteralPath $picker.FileName -Raw | ConvertFrom-Json
    if ($profile.branch_id -lt 1 -or $profile.station -notin @('KITCHEN','SWEETS','BEVERAGE','FAST_FOOD','BILLING')) { throw 'Invalid printer profile.' }
    $folder = Join-Path $env:LOCALAPPDATA "GokulPrint$($profile.branch_id)-$($profile.station)"
    if (Test-Path (Join-Path $folder 'config.local.json')) { throw 'This station is already installed. Manage it in Admin setup. Contact your administrator for an agent software upgrade.' }
    $python = Join-Path $env:LOCALAPPDATA 'ProgramsPythonPython313python.exe'
    if (!(Test-Path $python)) {
        Write-Host 'Installing Python 3.13 using Windows Package Manager...'
        if (!(Get-Command winget -ErrorAction SilentlyContinue)) { throw 'Install Microsoft App Installer (WinGet) first, then run this installer again.' }
        & winget install --id Python.Python.3.13 --exact --source winget --scope user --silent --accept-package-agreements --accept-source-agreements
        if ($LASTEXITCODE -ne 0 -or !(Test-Path $python)) { throw 'Python installation did not complete. Check Windows Package Manager and retry.' }
    }
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    foreach ($file in @('agent.py','background.py','requirements.txt')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $folder -Force }
    & $python -m venv (Join-Path $folder '.venv')
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the agent environment.' }
    $runtime = Join-Path $folder '.venvScriptspython.exe'
    & $runtime -m pip install -r (Join-Path $folder 'requirements.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Agent dependency installation failed.' }
    & $runtime (Join-Path $folder 'agent.py') --config $picker.FileName validate
    if ($LASTEXITCODE -ne 0) { throw 'Printer profile validation failed.' }
    $secureKey = Read-Host 'Private PRINT_AGENT_API_KEY (from your backend administrator)' -AsSecureString
    $plainKey = [System.Net.NetworkCredential]::new('', $secureKey).Password
    if ([string]::IsNullOrWhiteSpace($plainKey)) { throw 'The private agent key is required.' }
    $encrypted = [System.Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes($plainKey), $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
    [IO.File]::WriteAllBytes((Join-Path $folder 'key.dpapi'), $encrypted)
    $plainKey = $null
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $taskName = "GokulPrint-$($profile.branch_id)-$($profile.station)"
    $action = New-ScheduledTaskAction -Execute (Join-Path $folder '.venvScriptspythonw.exe') -Argument ('"' + (Join-Path $folder 'background.py') + '"') -WorkingDirectory $folder
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $identity
    $principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
    Copy-Item -LiteralPath $picker.FileName -Destination (Join-Path $folder 'config.local.json')
    Start-ScheduledTask -TaskName $taskName
    Write-Host 'Installed. Return to Admin setup, wait for Online, print a test, then Start printing.'
    Write-Host 'The agent starts automatically after this Windows account signs in. Keep Windows awake and signed in.'
} catch {
    Write-Host ('Installation failed: ' + $_.Exception.Message) -ForegroundColor Red
    # Leave the encrypted key and journal untouched; no destructive automatic cleanup.
    Write-Host 'If task registration failed, run this installer from the SAME shop Windows account with administrator rights. Ask your administrator to remove the incomplete config.local.json first.'
    exit 1
}
