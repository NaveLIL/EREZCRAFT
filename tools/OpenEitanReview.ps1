param([string]$RunRoot='D:/repos/Minecraft/EREZCRAFT/.verification/eitan-drive-native-10')
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$target=(Resolve-Path -LiteralPath $RunRoot).Path
if((Split-Path -Parent $target) -ne (Join-Path $workspace '.verification') -or (Split-Path -Leaf $target) -notmatch '^eitan-drive-[A-Za-z0-9_-]+$' -or (Get-Item -LiteralPath $target).LinkType){throw 'Direct Eitan verification root required'}
$acceptance=Get-Content -Raw -LiteralPath (Join-Path $workspace 'docs/verification/EITAN_DRIVE.json') | ConvertFrom-Json
if(-not $acceptance.passed){throw 'Complete Eitan acceptance is required before owner handover'}
$profile=Join-Path $target 'studioEitanDrive'
$proof=Get-Content -Raw -LiteralPath (Join-Path $profile 'eitan-drive-full.json') | ConvertFrom-Json
if(-not $proof.passed -or $proof.process_exit_code -ne 0 -or $proof.geometry_sha256 -ne $acceptance.source.geometry_sha256){throw 'Passed matching Eitan scene required'}
$games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'net\.neoforged\.devlaunch\.Main|cpw\.mods\.bootstraplauncher\.BootstrapLauncher|net\.minecraft\.client\.main\.Main'})
if($games.Count){throw 'Minecraft is already open; no game was closed'}
'Owner review. Automated tests must never reuse this profile.' | Set-Content -LiteralPath (Join-Path $profile 'eitan-owner-profile') -Encoding utf8
$env:JAVA_HOME=Join-Path $workspace '.verification/toolchain/jdk-21.0.12.1+1'
Push-Location -LiteralPath $workspace
try{
    Write-Host 'Choose the saved Eitan driving world. Automatic test input is disabled.'
    & "$env:JAVA_HOME/bin/java.exe" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain '-Pmod_version=0.7.0-preview.102-eitan-drive' "-PintersticeRunRoot=$target" '-PeitanPhase=review' "-PprogressionPackSource=$workspace/.verification/tide-release-20261028/client" runStudioEitanDrive
    if($LASTEXITCODE -ne 0){throw 'Review client failed; inspect its log'}
}finally{Pop-Location}
