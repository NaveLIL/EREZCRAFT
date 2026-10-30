param([string]$RunRoot='D:/repos/Minecraft/EREZCRAFT/.verification/eitan-r4-native-01')
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$parent=(Resolve-Path -LiteralPath (Split-Path -Parent $RunRoot)).Path
$leaf=Split-Path -Leaf $RunRoot
if($parent -ne (Join-Path $workspace '.verification') -or $leaf -notmatch '^eitan-[A-Za-z0-9_-]+$'){throw 'Separate direct .verification/eitan-* root required'}
$target=Join-Path $parent $leaf
if((Test-Path -LiteralPath $target) -and (Get-Item -LiteralPath $target).LinkType){throw 'Verification root cannot be a link'}
$profile=Join-Path $target 'studioEitan'
if(Test-Path -LiteralPath (Join-Path $profile 'saves')){throw 'A fresh disposable Eitan world is required'}
$games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'net\.neoforged\.devlaunch\.Main|cpw\.mods\.bootstraplauncher\.BootstrapLauncher|net\.minecraft\.client\.main\.Main'})
if($games.Count){throw "Minecraft already open: $($games.ProcessId -join ', '). This script never closes an owner game."}
New-Item -ItemType Directory -Path $profile -Force | Out-Null
'Automated geometry inspection only; never substitute an owner world.' | Set-Content -LiteralPath (Join-Path $profile 'eitan-automated-profile') -Encoding utf8
$env:JAVA_HOME=Join-Path $workspace '.verification/toolchain/jdk-21.0.12.1+1'
Push-Location -LiteralPath $workspace
try{
    $startedAt=Get-Date
    & "$env:JAVA_HOME/bin/java.exe" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain '-Pmod_version=0.7.0-preview.101-eitan-model' "-PintersticeRunRoot=$target" "-PprogressionPackSource=$workspace/.verification/tide-release-20261028/client" runStudioEitan
    $code=$LASTEXITCODE
    $proof=Join-Path $profile 'eitan-native.json'
    $fresh=(Test-Path -LiteralPath $proof) -and (Get-Item -LiteralPath $proof).LastWriteTime -ge $startedAt
    if($fresh){$report=Get-Content -Raw -LiteralPath $proof | ConvertFrom-Json;$report | Add-Member -NotePropertyName process_exit_code -NotePropertyValue $code -Force;$report | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $proof -Encoding utf8}
    if($code -ne 0 -or -not $fresh -or -not $report.passed){throw 'Eitan geometry did not pass; inspect isolated log/report'}
}finally{Pop-Location}
