param([string[]]$Tasks = @('test'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $javaCommand = Get-Command javac -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
    } else {
        $bundled = Get-ChildItem -Path 'C:\toyProject\eclipse\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_21*\jre\bin\javac.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($bundled) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $bundled.FullName) }
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\javac.exe")) {
    throw 'Set JAVA_HOME to a Java 21 JDK before running this script.'
}
# 실행 프로세스에만 환경 변수를 적용해 전역 Git과 시스템 설정에 영향을 주지 않는다.
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home' }
Push-Location $projectRoot
try {
    # Windows의 Gradle 변환 캐시 이동 오류를 우회한다(gradle/gradle#31438).
    # Gradle 계측 에이전트에만 적용되는 옵션이며 애플리케이션 보안 설정은 유지한다.
    & .\gradlew.bat @Tasks '-Dorg.gradle.internal.instrumentation.agent=false' --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle exited with $LASTEXITCODE" }
} finally { Pop-Location }
