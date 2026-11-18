# Perto: compila o app e instala em todos os celulares ligados no cabo.
# Uso:  & .\build-e-instala.ps1
. (Join-Path $PSScriptRoot '_ambiente.ps1')
$ErrorActionPreference = 'Continue'
$apk = Join-Path $projeto 'app\build\outputs\apk\debug\app-arm64-v8a-debug.apk'
& $gradlew -p $projeto --stop | Out-Null

Write-Host '== Compilando o Perto...' -ForegroundColor Cyan
& $gradlew -p $projeto :app:assembleDebug --console=plain -q
if ($LASTEXITCODE -ne 0) { Write-Host '== ERRO na compilacao' -ForegroundColor Red; exit 1 }
Write-Host '== Compilou!' -ForegroundColor Green

$celulares = & $adb devices | Select-String -Pattern '^(\S+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value }
if (-not $celulares) { Write-Host '== Nenhum celular no cabo. O APK esta em:' $apk -ForegroundColor Yellow; exit 0 }

foreach ($c in $celulares) {
    $modelo = (& $adb -s $c shell getprop ro.product.model).Trim()
    Write-Host "== Celular $modelo" -ForegroundColor Cyan
    & $adb -s $c push $apk /sdcard/Download/Perto.apk | Out-Null
    Write-Host '   copiado para Download/Perto.apk'
    Write-Host '   instalando pelo cabo (se aparecer aviso no celular, toque em Instalar)...'
    & $adb -s $c install -r $apk
}
Write-Host '== FIM' -ForegroundColor Green
