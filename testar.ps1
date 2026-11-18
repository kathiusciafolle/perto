# Perto: roda os testes automaticos e a analise de erros (lint) do app.
# Uso:  & .\testar.ps1            (testes + lint)
#       & .\testar.ps1 testes     (so os testes)
#       & .\testar.ps1 lint       (so o lint)
param([string]$o_que = 'tudo')
. (Join-Path $PSScriptRoot '_ambiente.ps1')

if ($o_que -in @('tudo', 'testes')) {
    Write-Host '== Rodando os testes automaticos...' -ForegroundColor Cyan
    & $gradlew -p $projeto :app:testDebugUnitTest --console=plain --continue *> (Join-Path $tmp 'testes.log')
    Write-Host "== Testes terminaram com codigo $LASTEXITCODE (log em .tmp\testes.log)"
}
if ($o_que -in @('tudo', 'lint')) {
    Write-Host '== Rodando o lint...' -ForegroundColor Cyan
    & $gradlew -p $projeto :app:lintDebug --console=plain --continue *> (Join-Path $tmp 'lint.log')
    Write-Host "== Lint terminou com codigo $LASTEXITCODE (log em .tmp\lint.log)"
}
Write-Host '== FIM' -ForegroundColor Green
