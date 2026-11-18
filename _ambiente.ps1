# Perto: prepara o ambiente para compilar (JDK 21, Android SDK e pasta temporaria propria).
# Os outros scripts carregam este arquivo com:  . (Join-Path $PSScriptRoot '_ambiente.ps1')

function Find-Jdk21 {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        $v = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Out-String
        if ($v -match 'version "21') { return $env:JAVA_HOME }
    }
    $pastas = @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu", "$env:USERPROFILE\.jdks")
    foreach ($p in $pastas) {
        if (Test-Path $p) {
            $jdk = Get-ChildItem $p -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '21' -and (Test-Path (Join-Path $_.FullName 'bin\javac.exe')) } |
                Select-Object -First 1
            if ($jdk) { return $jdk.FullName }
        }
    }
    return $null
}

$jdk = Find-Jdk21
if (-not $jdk) { Write-Host 'JDK 21 nao encontrado. Instale com: winget install EclipseAdoptium.Temurin.21.JDK' -ForegroundColor Red; exit 1 }
$env:JAVA_HOME = $jdk
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

$tmp = Join-Path $PSScriptRoot '.tmp'
New-Item -ItemType Directory -Force $tmp | Out-Null
$env:TEMP = $tmp; $env:TMP = $tmp
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$tmp"
$env:GRADLE_OPTS = "-Djava.io.tmpdir=$tmp"
$projeto = Join-Path $PSScriptRoot 'android'
$gradlew = Join-Path $projeto 'gradlew.bat'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
