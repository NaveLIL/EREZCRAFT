param([ValidateSet('create','reload')][string]$Phase='create',[string]$RunRoot='D:/repos/Minecraft/EREZCRAFT/.verification/ne01me01')
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$allowed=(Resolve-Path -LiteralPath (Join-Path $workspace '.verification')).Path
$parent=(Resolve-Path -LiteralPath (Split-Path -Parent $RunRoot)).Path
$leaf=Split-Path -Leaf $RunRoot
if($parent -ne $allowed -or $leaf -notmatch '^ne01[A-Za-z0-9_-]+$'){throw 'New NE01 verification root required'}
$games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'net\.neoforged\.devlaunch\.Main|cpw\.mods\.bootstraplauncher\.BootstrapLauncher|net\.minecraft\.client\.main\.Main'})
if($games.Count){throw "An existing Minecraft client is open (PID $($games.ProcessId -join ', ')); owner game is protected"}
$profile=Join-Path $RunRoot 'studioMarineAe2'
if(Test-Path -LiteralPath (Join-Path $profile 'marine-owner-profile')){throw 'Owner profile protected'}
if($Phase -eq 'create' -and (Test-Path -LiteralPath (Join-Path $profile 'saves'))){throw 'Disposable create root already used'}
if($Phase -eq 'reload'){
 $prior=Get-Content -Raw -LiteralPath (Join-Path $profile 'marine-create.json') | ConvertFrom-Json
 if(-not $prior.passed -or $prior.process_exit_code -ne 0){throw 'Clean successful first process required'}
}
New-Item -ItemType Directory -Path $RunRoot -Force | Out-Null
$env:JAVA_HOME=Join-Path $workspace '.verification/toolchain/jdk-21.0.12.1+1'
Push-Location -LiteralPath $workspace
try {
 $started=Get-Date
 & "$env:JAVA_HOME/bin/java.exe" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain '-Pmod_version=0.7.0-preview.101-ne01' "-PintersticeRunRoot=$RunRoot" "-PprogressionPackSource=$workspace/.verification/tide-release-20261028/client" "-PmarinePhase=$Phase" runStudioMarineAe2
 $code=$LASTEXITCODE
 $file=Join-Path $profile "marine-$Phase.json"
 if((Test-Path -LiteralPath $file) -and (Get-Item -LiteralPath $file).LastWriteTime -ge $started){$proof=Get-Content -Raw -LiteralPath $file | ConvertFrom-Json; $proof | Add-Member -NotePropertyName process_exit_code -NotePropertyValue $code -Force; $proof | ConvertTo-Json -Depth 30 | Set-Content -Encoding utf8 -LiteralPath $file}
 if($code -ne 0 -or -not $proof.passed){throw "Marine $Phase failed; inspect isolated logs"}
} finally {Pop-Location}
