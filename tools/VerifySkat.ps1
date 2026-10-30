param(
    [ValidateSet('create','resume','reload','glass','speed','thrust','thrust-create')][string]$Phase='create',
    [string]$RunRoot='D:/repos/Minecraft/EREZCRAFT/.verification/sk97n1',
    [switch]$ResetCapsizedFixture,[switch]$Fleet
)
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$allowed=Join-Path $workspace '.verification'
$parent=(Resolve-Path -LiteralPath (Split-Path -Parent $RunRoot)).Path
$leaf=Split-Path -Leaf $RunRoot
if($parent -ne $allowed -or $leaf -notmatch '^[A-Za-z0-9_-]+$'){throw 'A direct, separately named .verification root is required'}
$target=Join-Path $parent $leaf
if((Test-Path -LiteralPath $target) -and (Get-Item -LiteralPath $target).LinkType){throw 'Verification root must not be a link'}
$profile=Join-Path $target 'studioSkat'
if(Test-Path -LiteralPath (Join-Path $profile 'skat-owner-profile')){throw 'Owner review world is protected'}
$games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'net\.neoforged\.devlaunch\.Main|cpw\.mods\.bootstraplauncher\.BootstrapLauncher|net\.minecraft\.client\.main\.Main'})
if($games.Count){throw "An existing Minecraft client is open (PID $($games.ProcessId -join ', ')). Save and close it; this script never closes an owner game."}
New-Item -ItemType Directory -Path $target -Force | Out-Null
if($Phase -in @('create','thrust-create') -and (Test-Path -LiteralPath (Join-Path $profile 'saves'))){throw 'New disposable root required'}
if($Phase -notin @('create','thrust-create')){
    $priorFile=Join-Path $profile 'skat-create.json'
    if($Phase -eq 'reload' -and (Test-Path -LiteralPath (Join-Path $profile 'skat-resume.json'))){$priorFile=Join-Path $profile 'skat-resume.json'}
    $prior=Get-Content -Raw -LiteralPath $priorFile | ConvertFrom-Json
    if($prior.process_exit_code -ne 0 -or $prior.owner_profile_used -ne $false -or (-not $prior.passed -and -not ($Phase -eq 'resume' -and $prior.cargo_containers -eq 24 -and $prior.ship_uuid))){throw 'A clean previous native process with the real owned ship is required'}
}
$env:JAVA_HOME=Join-Path $workspace '.verification/toolchain/jdk-21.0.12.1+1'
Push-Location -LiteralPath $workspace
try{
    if($Phase -notin @('create','thrust-create')){& 'C:/Users/Dimon/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' (Join-Path $workspace 'tools/ReadSkatSavedPose.py') --profile $profile;if($LASTEXITCODE -ne 0){throw 'Saved ship pose could not be read safely'}}
    $startedAt=Get-Date
    $reset='false';if($ResetCapsizedFixture){if($Phase -ne 'resume'){throw 'Fixture recovery is restricted to resume'};$reset='true'}
    & "$env:JAVA_HOME/bin/java.exe" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain '-Pmod_version=0.7.0-preview.121-vehicle-fleet' "-PintersticeRunRoot=$target" "-PprogressionPackSource=$workspace/.verification/tide-release-20261028/client" "-PskatPhase=$Phase" "-PskatFleet=$($Fleet.IsPresent.ToString().ToLowerInvariant())" "-PskatFixtureReset=$reset" runStudioSkat
    $code=$LASTEXITCODE
    $proof=Join-Path $profile "skat-$Phase.json"
    $fresh=(Test-Path -LiteralPath $proof) -and (Get-Item -LiteralPath $proof).LastWriteTime -ge $startedAt
    if($fresh){$report=Get-Content -Raw -LiteralPath $proof | ConvertFrom-Json;$report | Add-Member -NotePropertyName process_exit_code -NotePropertyValue $code -Force;$report | ConvertTo-Json -Depth 30 | Set-Content -Encoding utf8 -LiteralPath $proof;if($Fleet -and $Phase -eq 'thrust-create' -and $report.passed){$report | ConvertTo-Json -Depth 30 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $profile 'skat-create.json')}}
    if($code -ne 0 -or -not $fresh -or -not $report.passed){throw "Skat $Phase did not pass; inspect the isolated log/report"}
}finally{Pop-Location}
