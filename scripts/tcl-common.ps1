# Funcoes compartilhadas pelos scripts da TCL BeyondTV2. Use com dot-source:
#   . "$PSScriptRoot\tcl-common.ps1"

# Configuracoes que os scripts podem alterar e que o restore devolve ao valor do backup.
$script:ManagedSettings = @(
    'secure/screensaver_enabled',
    'secure/screensaver_components',
    'secure/screensaver_default_component',
    'secure/screensaver_activate_on_sleep',
    'secure/screensaver_activate_on_dock',
    'secure/sleep_timeout',
    'secure/default_input_method',
    'secure/voice_interaction_service',
    'secure/voice_recognition_service',
    'secure/assistant',
    'secure/enabled_accessibility_services',
    'secure/accessibility_enabled',
    'global/window_animation_scale',
    'global/transition_animation_scale',
    'global/animator_duration_scale',
    'system/screen_off_timeout'
)

$script:HomeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

function Get-AdbPath {
    if ($env:ADB -and (Test-Path $env:ADB)) { return $env:ADB }
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $winget = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe'
    if (Test-Path $winget) { return $winget }
    throw 'adb.exe nao encontrado. Defina $env:ADB com o caminho completo.'
}

function Get-DefaultTvHost {
    if ($env:TCL_TV_HOST) { return $env:TCL_TV_HOST }
    throw 'Defina o IP da TV: $env:TCL_TV_HOST = ''<ip-da-tv>'' (Set the TV IP in TCL_TV_HOST)'
}

function Connect-Tv([string]$TvHost) {
    $script:Adb = Get-AdbPath
    $serial = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }
    $connectOut = (& $script:Adb connect $serial | Out-String).Trim()
    $state = (& $script:Adb -s $serial get-state | Out-String).Trim()
    if ($state -ne 'device') {
        throw "TV $serial nao esta pronta (estado: '$state'). adb connect: $connectOut"
    }
    $script:Serial = $serial
    return $serial
}

# Um comando por chamada: encadear varios 'pm' na mesma linha pode parar no primeiro.
function Invoke-TvShell([string]$Command) {
    $lines = & $script:Adb -s $script:Serial shell $Command
    return @($lines | ForEach-Object { "$_".TrimEnd("`r") })
}

function Get-TvPackages([string]$Flags = '') {
    return @(Invoke-TvShell "pm list packages $Flags" |
        Where-Object { $_ -like 'package:*' } |
        ForEach-Object { $_.Substring(8).Trim() } |
        Sort-Object -Unique)
}

function Get-TvHome {
    return (Invoke-TvShell $script:HomeQuery | Where-Object { $_ -match '/' } | Select-Object -Last 1)
}

function Get-TvSetting([string]$NsKey) {
    $ns, $key = $NsKey -split '/', 2
    return ((Invoke-TvShell "settings get $ns $key") -join '').Trim()
}

function Get-ProtectedPackages {
    $file = Join-Path $PSScriptRoot 'protected-packages.txt'
    return @(Get-Content $file -Encoding UTF8 |
        ForEach-Object { ($_ -replace '#.*$', '').Trim() } |
        Where-Object { $_ })
}
