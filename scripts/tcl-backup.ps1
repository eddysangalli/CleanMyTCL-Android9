<#
.SYNOPSIS
  Fotografa o estado da TCL (pacotes, configuracoes, Home) em backup\<data-hora>\. Somente leitura.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\tcl-backup.ps1
#>
param(
    [string]$TvHost = '',
    [string]$OutRoot = ''
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\tcl-common.ps1"
if (-not $TvHost) { $TvHost = Get-DefaultTvHost }
if (-not $OutRoot) { $OutRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'backup' }

$serial = Connect-Tv $TvHost
$dir = Join-Path $OutRoot (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Force -Path $dir | Out-Null
function Save([string]$Name, $Lines) { $Lines | Set-Content -Encoding UTF8 -Path (Join-Path $dir $Name) }

Write-Host "Backup de $serial em $dir"
Save 'getprop.txt' (Invoke-TvShell 'getprop')
foreach ($ns in 'global', 'secure', 'system') { Save "settings_$ns.txt" (Invoke-TvShell "settings list $ns") }

$installed = Get-TvPackages
$disabled = Get-TvPackages '-d'
Save 'packages_installed.txt' $installed
Save 'packages_disabled.txt' $disabled
Save 'packages_system.txt' (Get-TvPackages '-s')
Save 'packages_thirdparty.txt' (Get-TvPackages '-3')
Save 'packages_detail.txt' (Invoke-TvShell 'pm list packages -f -U -i -u')
Save 'home_resolvers.txt' (Invoke-TvShell 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
Save 'preferred_activities.xml' (Invoke-TvShell 'dumpsys package preferred-xml')
Save 'dumpsys_package.txt' (Invoke-TvShell 'dumpsys package packages')

$homeComponent = Get-TvHome
$settings = [ordered]@{}
foreach ($k in $script:ManagedSettings) { $settings[$k] = Get-TvSetting $k }

$state = [ordered]@{
    created           = (Get-Date).ToString('s')
    serial            = $serial
    model             = ((Invoke-TvShell 'getprop ro.product.model') -join '').Trim()
    fingerprint       = ((Invoke-TvShell 'getprop ro.build.fingerprint') -join '').Trim()
    software          = ((Invoke-TvShell 'getprop ro.software.version_id') -join '').Trim()
    home              = $homeComponent
    installedPackages = $installed
    disabledPackages  = $disabled
    managedSettings   = $settings
}
$state | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path (Join-Path $dir 'state.json')

# O primeiro backup vira a referencia (baseline) usada por padrao no restore.
$baselineFile = Join-Path $OutRoot 'BASELINE.txt'
if (-not (Test-Path $baselineFile)) {
    Split-Path $dir -Leaf | Set-Content -Encoding UTF8 -Path $baselineFile
    Write-Host "Marcado como baseline: $(Split-Path $dir -Leaf)"
}

Write-Host ("OK: {0} pacotes instalados, {1} desativados, Home = {2}" -f $installed.Count, $disabled.Count, $homeComponent)
