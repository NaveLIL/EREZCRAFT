param([ValidateSet('create','reload')][string]$Phase='create',[string]$RunRoot='D:/repos/Minecraft/EREZCRAFT/.verification/ne01m01')
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$allowed=(Resolve-Path -LiteralPath (Join-Path $workspace '.verification')).Path
$parent=(Resolve-Path -LiteralPath (Split-Path -Parent $RunRoot)).Path
if($parent -ne $allowed -or (Split-Path -Leaf $RunRoot) -notmatch '^ne01m[A-Za-z0-9_-]+$'){throw 'Disposable NE01 ME root required'}
$games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'net\.neoforged\.devlaunch\.Main|cpw\.mods\.bootstraplauncher\.BootstrapLauncher|net\.minecraft\.client\.main\.Main'})
if($games.Count){throw "Minecraft already running (PID $($games.ProcessId -join ', ')); no second client"}
$profile=Join-Path $RunRoot 'studioMarineAe2'
if(Test-Path -LiteralPath (Join-Path $profile 'marine-owner-profile')){throw 'Owner profile protected'}
if($Phase -eq 'create' -and (Test-Path -LiteralPath $RunRoot)){throw 'New disposable full ME root required'}
if($Phase -eq 'reload'){
 $prior=Get-Content -Raw -LiteralPath (Join-Path $profile 'marine-fullme-create.json') | ConvertFrom-Json
 if(-not $prior.passed -or $prior.process_exit_code -ne 0){throw 'Clean successful create process required'}
 $layout=Get-Content -Raw -LiteralPath (Join-Path $workspace 'src/main/resources/data/interstice/ne01/ne01.json') | ConvertFrom-Json
 if($prior.layout_revision -ne $layout.revision){throw 'Historical NE01 world uses a different cell layout; create a new isolated profile'}
 if($prior.geometry_sha256 -ne $layout.geometry_sha256){throw 'Historical NE01 geometry changed; new isolated profile required'}
}
New-Item -ItemType Directory -Path $RunRoot -Force | Out-Null
$env:JAVA_HOME=Join-Path $workspace '.verification/toolchain/jdk-21.0.12.1+1'
Push-Location -LiteralPath $workspace
try {
 & "$env:JAVA_HOME/bin/java.exe" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain '-Pmod_version=0.7.0-preview.103-ne01-me' "-PintersticeRunRoot=$RunRoot" "-PprogressionPackSource=$workspace/.verification/tide-release-20261028/client" "-PmarinePhase=wholeme-$Phase" runStudioMarineAe2
 $code=$LASTEXITCODE
 $file=Join-Path $profile "marine-fullme-$Phase.json"
 if(-not(Test-Path -LiteralPath $file)){throw 'No native full ME evidence'}
 $proof=Get-Content -Raw -LiteralPath $file | ConvertFrom-Json
 $proof | Add-Member -NotePropertyName process_exit_code -NotePropertyValue $code -Force
 $proof | ConvertTo-Json -Depth 40 | Set-Content -Encoding utf8 -LiteralPath $file
 if($code -ne 0 -or -not $proof.passed){throw 'NE01 full ME native check failed; inspect owned logs'}
}finally{Pop-Location}
