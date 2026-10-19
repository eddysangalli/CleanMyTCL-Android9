<#
.SYNOPSIS
  Desativa ou reativa UM pacote por vez, confere o resultado e registra em backup\changes.log.
  Recusa pacotes de protected-packages.txt, salvo com -Force.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\tcl-pkg.ps1 status com.tcl.esticker
  powershell -ExecutionPolicy Bypass -File scripts\tcl-pkg.ps1 disable com.tcl.esticker
  powershell -ExecutionPolicy Bypass -File scripts\tcl-pkg.ps1 enable com.tcl.esticker
#>
param(
    [Parameter(Mandatory = $true, Position = 0)][ValidateSet('status', 'disable', 'enable')][string]$Action,
    [Parameter(Mandatory = $true, Position = 1)][string]$Package,
    [string]$TvHost = '',
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\tcl-common.ps1"
if (-not $TvHost) { $TvHost = Get-DefaultTvHost }
$serial = Connect-Tv $TvHost

if ((Get-TvPackages) -notcontains $Package) { throw "$Package nao esta instalado na TV." }
$wasDisabled = (Get-TvPackages '-d') -contains $Package
$stateText = if ($wasDisabled) { 'desativado' } else { 'ativo' }

if ($Action -eq 'status') { Write-Host "$Package esta $stateText"; exit 0 }

if ($Action -eq 'disable') {
    if ((Get-ProtectedPackages) -contains $Package -and -not $Force) {
        throw "$Package esta em protected-packages.txt. Use -Force so com aprovacao explicita."
    }
    if ($wasDisabled) { Write-Host "$Package ja estava desativado."; exit 0 }
    $out = (Invoke-TvShell "pm disable-user --user 0 $Package") -join ' '
    $ok = (Get-TvPackages '-d') -contains $Package
} else {
    if (-not $wasDisabled) { Write-Host "$Package ja estava ativo."; exit 0 }
    $out = (Invoke-TvShell "pm enable $Package") -join ' '
    $ok = (Get-TvPackages '-d') -notcontains $Package
}

$log = Join-Path (Join-Path (Split-Path $PSScriptRoot -Parent) 'backup') 'changes.log'
"{0}`t{1}`t{2}`t{3}`t{4}" -f (Get-Date).ToString('s'), $Action, $Package, $(if ($ok) { 'OK' } else { 'FALHOU' }), $out |
    Add-Content -Encoding UTF8 -Path $log
Write-Host "$Action $Package -> $out ($(if ($ok) { 'conferido' } else { 'NAO conferido' }))"
if (-not $ok) { exit 1 }
