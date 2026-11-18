# Perto: traz as novidades do bitchat original para um branch separado, sem mexer no main.
# - Le em BASE-BITCHAT.txt o commit do bitchat em que o Perto esta baseado.
# - Busca o bitchat atual e aplica so as diferencas (base -> atual) dentro de android/.
# - Cria o branch atualizacao-bitchat-AAAA-MM-DD com o resultado e volta para o main.
# Nao envia nada para o GitHub e nao publica release.
# Saida (ultima linha): RESULTADO=SEM_NOVIDADES | RESULTADO=APLICADO | RESULTADO=CONFLITO | RESULTADO=ERRO
param([switch]$ManterNoBranch)
$ErrorActionPreference = 'Continue'
$env:Path = (Join-Path $env:ProgramFiles 'Git\cmd') + ';' + $env:Path
Set-Location $PSScriptRoot

function Sair($r) { Write-Host "RESULTADO=$r"; exit 0 }

if (git status --porcelain) { Write-Host 'Ha mudancas nao salvas no repositorio; nada foi feito.'; Sair 'ERRO' }
$atual = (git rev-parse --abbrev-ref HEAD).Trim()
if ($atual -ne 'main') { Write-Host "O repositorio esta no branch '$atual', nao no main; nada foi feito."; Sair 'ERRO' }

$base = (Get-Content (Join-Path $PSScriptRoot 'BASE-BITCHAT.txt') -TotalCount 1).Trim()
if (-not (git remote | Select-String -Pattern '^bitchat$' -Quiet)) {
    git remote add bitchat https://github.com/permissionlesstech/bitchat-android.git
}
Write-Host '== Buscando o bitchat original...'
git fetch -q bitchat main
if ($LASTEXITCODE -ne 0) { Write-Host 'Nao consegui baixar o bitchat (sem internet?).'; Sair 'ERRO' }
$novo = (git rev-parse bitchat/main).Trim()
Write-Host "Base do Perto: $base"
Write-Host "Bitchat agora: $novo"
if ($novo -eq $base) { Write-Host 'Nenhuma mudanca no bitchat desde a ultima atualizacao.'; Sair 'SEM_NOVIDADES' }

Write-Host '== Mudancas no bitchat desde a base:'
git log --oneline "$base..$novo"
Write-Host '== Arquivos alterados:'
git diff --stat "$base" "$novo" | Select-Object -Last 40

$branch = 'atualizacao-bitchat-' + (Get-Date -Format 'yyyy-MM-dd')
if (git branch --list $branch) { $branch = $branch + '-' + (Get-Date -Format 'HHmm') }
git switch -q -c $branch

$patch = Join-Path $PSScriptRoot '.tmp\bitchat.patch'
New-Item -ItemType Directory -Force (Split-Path $patch) | Out-Null
git diff --binary "$base" "$novo" | Out-File -FilePath $patch -Encoding ascii
git apply --3way --whitespace=nowarn --directory=android $patch
$conflitos = git diff --name-only --diff-filter=U
if ($LASTEXITCODE -ne 0 -and -not $conflitos) {
    Write-Host 'O git nao conseguiu aplicar as mudancas.'
    git reset -q --hard; git switch -q main; git branch -q -D $branch
    Sair 'ERRO'
}
Set-Content -Path (Join-Path $PSScriptRoot 'BASE-BITCHAT.txt') -Value $novo -Encoding ascii
if ($conflitos) {
    Write-Host "== CONFLITOS (o Perto e o bitchat mexeram no mesmo trecho) no branch ${branch}:"
    $conflitos | ForEach-Object { Write-Host "   $_" }
    Write-Host 'O repositorio ficou no branch de atualizacao para resolver os conflitos.'
    Sair 'CONFLITO'
}
git add -A
git commit -q -m "Atualiza com o bitchat original ($($novo.Substring(0,7)))" -m "Mudancas do bitchat de $($base.Substring(0,7)) ate $($novo.Substring(0,7))." -m 'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'
Write-Host "== Mudancas aplicadas no branch $branch."
if (-not $ManterNoBranch) { git switch -q main }
Sair 'APLICADO'
