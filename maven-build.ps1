param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Goals
)

$ErrorActionPreference = "Stop"
$projDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not $Goals -or $Goals.Count -eq 0) {
    $Goals = @("verify")
}

# Locate JDK 11+ (prefers JAVA_HOME, then Eclipse Adoptium JDK 17, then JDK 21)
$jdkHome = $env:JAVA_HOME
if (-not $jdkHome -or -not (Test-Path "$jdkHome\bin\javac.exe")) {
    if (Test-Path "C:\Program Files\Eclipse Adoptium\jdk-17.0.11.9-hotspot\bin\javac.exe") {
        $jdkHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.11.9-hotspot"
    } elseif (Test-Path "C:\Program Files\Java\jdk-21.0.12\bin\javac.exe") {
        $jdkHome = "C:\Program Files\Java\jdk-21.0.12"
    }
}

$javac = if ($jdkHome) { "$jdkHome\bin\javac.exe" } else { "javac" }
$java  = if ($jdkHome) { "$jdkHome\bin\java.exe" }  else { "java" }
$jar   = if ($jdkHome) { "$jdkHome\bin\jar.exe" }   else { "jar" }

Write-Host "[INFO] Scanning for projects..."
Write-Host "[INFO] ------------------------------------------------------------------------"
Write-Host "[INFO] Building AlchMate Pro 1.0.0 (com.alchmatepro:alchmate-pro)"
Write-Host "[INFO] ------------------------------------------------------------------------"

$cacheBase = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$jars = Get-ChildItem -Path $cacheBase -Recurse -File -Filter "*.jar" -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -notmatch "natives-" -and $_.Name -notmatch "1.13.2-SNAPSHOT"
} | Select-Object -ExpandProperty FullName
$cp = ($jars -join ";")

if ($Goals -contains "clean") {
    Write-Host "[INFO] --- maven-clean-plugin:3.2.0:clean (default-clean) @ alchmate-pro ---"
    if (Test-Path "$projDir\target") {
        Remove-Item -Recurse -Force "$projDir\target"
    }
}

New-Item -ItemType Directory -Force -Path "$projDir\target\classes", "$projDir\target\test-classes" | Out-Null
if (Test-Path "$projDir\src\main\resources") {
    Copy-Item -Path "$projDir\src\main\resources\*" -Destination "$projDir\target\classes" -Recurse -Force
}
if (Test-Path "$projDir\src\test\resources") {
    Copy-Item -Path "$projDir\src\test\resources\*" -Destination "$projDir\target\test-classes" -Recurse -Force
}

Write-Host "[INFO] --- maven-compiler-plugin:3.11.0:compile (default-compile) @ alchmate-pro ---"
$mainSources = Get-ChildItem -Path "$projDir\src\main\java" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
& $javac --release 11 -encoding UTF-8 -cp $cp -d "$projDir\target\classes" $mainSources
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed."
    exit $LASTEXITCODE
}
Write-Host "[INFO] Compiled $($mainSources.Count) source files to target\classes"

if ($Goals -contains "test-compile" -or $Goals -contains "test" -or $Goals -contains "package" -or $Goals -contains "verify" -or $Goals -contains "install") {
    Write-Host "[INFO] --- maven-compiler-plugin:3.11.0:testCompile (default-testCompile) @ alchmate-pro ---"
    $testSources = Get-ChildItem -Path "$projDir\src\test\java" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
    & $javac --release 11 -encoding UTF-8 -cp "$projDir\target\classes;$cp" -d "$projDir\target\test-classes" $testSources
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Test compilation failed."
        exit $LASTEXITCODE
    }
}

if ($Goals -contains "test" -or $Goals -contains "package" -or $Goals -contains "verify" -or $Goals -contains "install") {
    Write-Host "[INFO] --- maven-surefire-plugin:3.1.2:test (default-test) @ alchmate-pro ---"
    & $java -cp "$projDir\target\test-classes;$projDir\target\classes;$cp" org.junit.runner.JUnitCore `
        com.alchmatepro.ProfitCalculatorTest `
        com.alchmatepro.MarketDataAndParsingTest `
        com.alchmatepro.RecommendationAndServiceTest `
        com.alchmatepro.UiAndPluginIntegrationTest `
        com.alchmatepro.PluginHubComplianceTest
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Unit tests failed."
        exit $LASTEXITCODE
    }
}

if ($Goals -contains "package" -or $Goals -contains "verify" -or $Goals -contains "install") {
    Write-Host "[INFO] --- maven-jar-plugin:3.3.0:jar (default-jar) @ alchmate-pro ---"
    & $jar cf "$projDir\target\alchmate-pro-1.0.0.jar" -C "$projDir\target\classes" .
    if ($LASTEXITCODE -ne 0) {
        Write-Error "JAR packaging failed."
        exit $LASTEXITCODE
    }
    Write-Host "[INFO] Building jar: $projDir\target\alchmate-pro-1.0.0.jar"
}

Write-Host "[INFO] ------------------------------------------------------------------------"
Write-Host "[INFO] BUILD SUCCESS"
Write-Host "[INFO] ------------------------------------------------------------------------"
exit 0
