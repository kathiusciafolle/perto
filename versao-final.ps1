# Perto: compila a versao final (release), assinada com a chave do Perto, e confere a assinatura.
# A chave fica fora do repositorio, em ~/.perto/ (veja o README).
. (Join-Path $PSScriptRoot '_ambiente.ps1')
$props = if ($env:PERTO_KEYSTORE_PROPERTIES) { $env:PERTO_KEYSTORE_PROPERTIES } else { Join-Path $env:USERPROFILE '.perto\keystore.properties' }
if (-not (Test-Path $props)) { Write-Host "== Chave do Perto nao encontrada ($props). A versao final sairia sem assinatura." -ForegroundColor Red; exit 1 }

Write-Host '== Compilando a versao final (demora alguns minutos)...' -ForegroundColor Cyan
& $gradlew -p $projeto :app:assembleRelease --console=plain *> (Join-Path $tmp 'release.log')
if ($LASTEXITCODE -ne 0) { Write-Host '== ERRO na compilacao (veja .tmp\release.log)' -ForegroundColor Red; exit 1 }

$saida = Join-Path $projeto 'app\build\outputs\apk\release'
$buildTools = Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
Get-ChildItem $saida -Filter *.apk | ForEach-Object {
    $cert = & $apksigner verify --print-certs $_.FullName 2>&1 | Select-String -Pattern 'SHA-256 digest'
    Write-Host ("== {0} ({1:N1} MB)" -f $_.Name, ($_.Length / 1MB))
    Write-Host "   $cert"
}
Write-Host "== APKs em: $saida" -ForegroundColor Green
