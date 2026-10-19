<#
.SYNOPSIS
  Devolve a TCL ao estado de um backup: reativa pacotes, restaura o Home original e as configuracoes
  gerenciadas. Sem -Apply so mostra o que faria (simulacao).
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\tcl-restore.ps1            # simulacao contra o baseline
  powershell -ExecutionPolicy Bypass -File scripts\tcl-restore.ps1 -Apply     # executa
#>
param(
    [string]$TvHost = '',
    [string]$Backup = '',            # pasta do backup; padrao: backup\BASELINE.txt
    [switch]$Apply,
    [switch]$RedisableBaseline       # tambem volta a desativar o que estava desativado no backup
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\tcl-common.ps1"
if (-not $TvHost) { $TvHost = Get-DefaultTvHost }
$backupRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'backup'
if (-not $Backup) {
    $Backup = Join-Path $backupRoot ((Get-Content (Join-Path $backupRoot 'BASELINE.txt') -Encoding UTF8 | Select-Object -First 1).Trim())
}
$state = Get-Content (Join-Path $Backup 'state.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$serial = Connect-Tv $TvHost

function Get-RestorePlan {
    $plan = New-Object System.Collections.Generic.List[string]
    $notes = New-Object System.Collections.Generic.List[string]
    $installed = Get-TvPackages
    $disabled = Get-TvPackages '-d'

    foreach ($p in $state.installedPackages) {
        if ($installed -notcontains $p) { $plan.Add("cmd package install-existing --user 0 $p") }
    }
    foreach ($p in $disabled) {
        if ($state.disabledPackages -notcontains $p) { $plan.Add("pm enable $p") }
    }
    foreach ($p in $state.disabledPackages) {
        if ($installed -contains $p -and $disabled -notcontains $p) {
            if ($RedisableBaseline) { $plan.Add("pm disable-user --user 0 $p") }
            else { $notes.Add("$p estava desativado no backup e agora esta ativo (use -RedisableBaseline)") }
        }
    }
    foreach ($prop in $state.managedSettings.PSObject.Properties) {
        $want = "$($prop.Value)"
        if ((Get-TvSetting $prop.Name) -ne $want) {
            $ns, $key = $prop.Name -split '/', 2
            if ($want -eq 'null') { $plan.Add("settings delete $ns $key") }
            else { $plan.Add("settings put $ns $key '$want'") }
        }
    }
    # O launcher original tem prioridade 2; reativado, ele volta a ser o Home sozinho.
    # set-home-activity cobre firmwares em que isso nao acontece.
    $homeNow = Get-TvHome
    if ($state.home -and $homeNow -ne $state.home) {
        $plan.Add("cmd package set-home-activity --user 0 $($state.home)")
    }
    return @{ Plan = $plan; Notes = $notes; Home = $homeNow }
}

Write-Host "Backup de referencia: $Backup ($($state.created))"
$r = Get-RestorePlan
foreach ($n in $r.Notes) { Write-Host "Aviso: $n" }
if ($r.Plan.Count -eq 0) {
    Write-Host "Nada a restaurar: a TV ja esta igual ao backup (Home = $($r.Home))."
    exit 0
}
Write-Host "Comandos necessarios ($($r.Plan.Count)):"
$r.Plan | ForEach-Object { Write-Host "  $_" }
if (-not $Apply) {
    Write-Host 'Simulacao. Rode de novo com -Apply para executar.'
    exit 0
}

$log = Join-Path $backupRoot ("restore-{0}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
foreach ($cmd in $r.Plan) {
    $out = (Invoke-TvShell $cmd) -join ' '
    "{0}`t{1}`t{2}" -f (Get-Date).ToString('s'), $cmd, $out | Add-Content -Encoding UTF8 -Path $log
    Write-Host "  $cmd -> $out"
}
$after = Get-RestorePlan
if ($after.Plan.Count -eq 0) {
    Write-Host "Restaurado. Home = $($after.Home). Log: $log"
} else {
    Write-Host "Ainda ha diferencas:"; $after.Plan | ForEach-Object { Write-Host "  $_" }
    exit 1
}
