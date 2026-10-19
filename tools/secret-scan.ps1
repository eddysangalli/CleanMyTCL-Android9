# Secret / privacy scan for CleanMyTCL-Android9. Read-only. Exit 1 if anything suspicious is tracked
# or staged. Usage: powershell -ExecutionPolicy Bypass -File tools\secret-scan.ps1
$ErrorActionPreference = 'Stop'
Set-Location (git rev-parse --show-toplevel)

$files = @(git ls-files) + @(git diff --cached --name-only) | Sort-Object -Unique | Where-Object { Test-Path $_ -PathType Leaf }

$patterns = [ordered]@{
    'private IPv4'      = '\b(10\.\d{1,3}|192\.168|172\.(1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}\b'
    'MAC address'       = '\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\b'
    'serial property'   = 'ro\.(serialno|boot\.serialno)\s*[\]:=]\s*\[?[A-Za-z0-9]{6,}'
    'android_id'        = 'android_id\s*[=:]\s*[0-9a-f]{12,}'
    'private key'       = '-----BEGIN [A-Z ]*PRIVATE KEY-----'
    'API key/token'     = '(?i)(api[_-]?key|secret|token|passw(or)?d)\s*[=:]\s*["''][^"'']{8,}'
    'Google API key'    = 'AIza[0-9A-Za-z_\-]{35}'
    'GitHub token'      = 'gh[pousr]_[A-Za-z0-9]{36,}'
    'email (non-noreply)' = '[A-Za-z0-9._%+-]+@(?!users\.noreply\.github\.com)[A-Za-z0-9.-]+\.[a-z]{2,}'
}
$badNames = '(?i)(adbkey|\.jks$|\.keystore$|\.apk$|\.p12$|\.pem$|^backup/|^private/|^medicoes/|state\.json$|dumpsys|getprop)'

# Enderecos privados usados como exemplo na documentacao. Nao sao o endereco real de
# nenhuma TV: 192.168.1.50 e o exemplo do proprio upstream (tv-vibehack) e o resto e o
# intervalo de documentacao padrao. Um IP real fora desta lista continua sendo sinalizado.
$placeholderIPs = @(
    '192.168.1.50',     # exemplo do upstream, usado tambem nos nossos docs
    '192.168.1.1',
    '192.168.0.1',
    '192.168.1.100',
    '10.0.0.1',
    '172.16.0.1'
)
# Nomes de servico do Android que containem '@' e se parecem com email.
$notEmails = @(
    'android\.hardware\.drm@\d\.\d-service\.widevine'
)

$hits = 0
$realHits = 0
foreach ($f in $files) {
    if ($f -match $badNames) { Write-Host "FILE   $f (should not be tracked)"; $hits++; $realHits++ }
    if ($f -match '\.(png|jpg|jpeg|ttf|jar|webp)$') { continue }
    $text = Get-Content -Raw -LiteralPath $f -ErrorAction SilentlyContinue
    if (-not $text) { continue }
    foreach ($k in $patterns.Keys) {
        foreach ($m in [regex]::Matches($text, $patterns[$k])) {
            $value = $m.Value
            # Ignora os falsos positivos conhecidos.
            $skip = $false
            if ($k -eq 'private IPv4' -and $placeholderIPs -contains $value) { $skip = $true }
            if ($k -like 'email*') {
                foreach ($ne in $notEmails) { if ($value -match $ne) { $skip = $true } }
            }
            if ($skip) {
                Write-Host ("{0,-20} {1}: {2}  (exemplo conhecido, ignorado)" -f $k, $f, $value)
                continue
            }
            Write-Host ("{0,-20} {1}: {2}" -f $k, $f, $value)
            $hits++
            $realHits++
        }
    }
}
Write-Host "`n$hits finding(s) in $($files.Count) files ($realHits need review)."
if ($realHits) { exit 1 }
