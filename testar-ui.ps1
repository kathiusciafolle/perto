# Perto: roda so os testes de interface + compila o APK.
. (Join-Path $PSScriptRoot '_ambiente.ps1')
Write-Host '== Testes de interface...' -ForegroundColor Cyan
& $gradlew -p $projeto :app:testDebugUnitTest --tests 'com.bitchat.android.ui.*' --console=plain --continue *> (Join-Path $tmp 'testes-ui.log')
Write-Host "== Testes de interface: codigo $LASTEXITCODE"
Write-Host '== Compilando o APK...' -ForegroundColor Cyan
& $gradlew -p $projeto :app:assembleDebug --console=plain -q *> (Join-Path $tmp 'apk.log')
Write-Host "== APK: codigo $LASTEXITCODE"
Write-Host '== FIM' -ForegroundColor Green
