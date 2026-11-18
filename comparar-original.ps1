# Perto: roda os testes que falharam no codigo ORIGINAL do bitchat (sem as mudancas do Perto),
# para saber se as falhas ja existiam antes.
. (Join-Path $PSScriptRoot '_ambiente.ps1')
$ErrorActionPreference = 'Stop'

$base = Join-Path $tmp 'original'
$zip = Join-Path $tmp 'bitchat-original.zip'
if (-not (Test-Path $base)) {
    Write-Host '== Baixando o codigo original...' -ForegroundColor Cyan
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri 'https://codeload.github.com/permissionlesstech/bitchat-android/zip/refs/heads/main' -OutFile $zip -Headers @{ 'User-Agent' = 'generic-client' }
    Expand-Archive -Path $zip -DestinationPath $base -Force
}
$projeto = Get-ChildItem $base -Directory | Select-Object -First 1 | ForEach-Object FullName
Write-Host "== Projeto original em $projeto"
$ErrorActionPreference = 'Continue'
& (Join-Path $projeto 'gradlew.bat') -p $projeto :app:testDebugUnitTest `
    --tests 'com.bitchat.android.services.ConversationDatabaseTest' `
    --tests 'com.bitchat.android.services.ConversationRepositoryTest' `
    --tests '*IncomingMessageAdmissionTest' `
    --tests 'com.bitchat.android.ui.MediaSendingManagerMigrationTest' `
    --tests '*NostrDirectMessageHandlerTest' `
    --console=plain --continue *> (Join-Path $tmp 'original-testes.log')
Write-Host "== Testes no original terminaram com codigo $LASTEXITCODE (log em .tmp\original-testes.log)"
Write-Host '== FIM' -ForegroundColor Green
