param([switch]$PrepareOnly)

$ErrorActionPreference = 'Stop'
if (!$PrepareOnly -and (Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft NeoForge*' })) {
    throw 'Save your world and close the existing Minecraft NeoForge client before resuming this profile.'
}
$repository = Split-Path -Parent $PSScriptRoot
$portableJava = Join-Path $repository '.verification\toolchain\jdk-21.0.12.1+1\bin\java.exe'
if (Test-Path -LiteralPath $portableJava) {
    $java = $portableJava
} elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $java = (Get-Command java -ErrorAction Stop).Source
}
$wrapper = Join-Path $repository 'gradle\wrapper\gradle-wrapper.jar'
$task = if ($PrepareOnly) { 'preparePlaytestResumeRun' } else { 'runPlaytestResume' }
& $java -classpath $wrapper org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain -p $repository $task
exit $LASTEXITCODE
