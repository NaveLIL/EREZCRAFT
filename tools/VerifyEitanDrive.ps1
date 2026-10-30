param([ValidateSet('chassis','full','fleet','reload','shader','fields','fields-reload','fields-route','fields-inspect')][string]$Phase='chassis',[string]$RunRoot='D:/repos/Minecraft/EREZCRAFT/.verification/eitan-drive-native-01',[switch]$WaitForClientExit)
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$parent=(Resolve-Path -LiteralPath (Split-Path -Parent $RunRoot)).Path
$leaf=Split-Path -Leaf $RunRoot
if($parent -ne (Join-Path $workspace '.verification') -or $leaf -notmatch '^eitan-drive-[A-Za-z0-9_-]+$'){throw 'Separate direct .verification/eitan-drive-* root required'}
$target=Join-Path $parent $leaf
if((Test-Path -LiteralPath $target) -and (Get-Item -LiteralPath $target).LinkType){throw 'Verification root cannot be a link'}
$profile=Join-Path $target 'studioEitanDrive'
if($Phase -notin @('reload','shader','fields-reload','fields-route','fields-inspect') -and (Test-Path -LiteralPath (Join-Path $profile 'saves'))){throw 'Fresh disposable driving world required'}
if($Phase -in @('reload','shader','fields-reload','fields-route','fields-inspect') -and (-not (Test-Path -LiteralPath (Join-Path $profile 'eitan-automated-profile')) -or (Test-Path -LiteralPath (Join-Path $profile 'eitan-owner-profile')))){throw 'Only an automated Eitan profile may be reloaded'}
$slotDeadline=(Get-Date).AddMinutes(30)
do{
    $games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'net\.neoforged\.devlaunch\.Main|cpw\.mods\.bootstraplauncher\.BootstrapLauncher|net\.minecraft\.client\.main\.Main'})
    if(-not $games.Count){break}
    if(-not $WaitForClientExit -or (Get-Date) -ge $slotDeadline){throw "Minecraft already open: $($games.ProcessId -join ', '). No game was closed."}
    Write-Host "Waiting for existing Minecraft to finish: $($games.ProcessId -join ', ')"
    Start-Sleep -Seconds 5
}while($true)
New-Item -ItemType Directory -Path $profile -Force | Out-Null
'Automated driving verification. No owner saves.' | Set-Content -LiteralPath (Join-Path $profile 'eitan-automated-profile') -Encoding utf8
$env:JAVA_HOME=Join-Path $workspace '.verification/toolchain/jdk-21.0.12.1+1'
function Get-EitanSourceHashes {
    $taskHashes=@{}
    $taskSources=@(& rg --files (Join-Path $workspace 'src/main/java/pro/erez/interstice/ship') (Join-Path $workspace 'src/main/java/pro/erez/interstice/vehicle') (Join-Path $workspace 'src/main/java/pro/erez/interstice/client') (Join-Path $workspace 'src/main/java/pro/erez/interstice/mixin') -g 'Eitan*.java' -g 'Vehicle*.java' -g 'Skat*.java')
    $taskSources+=Join-Path $workspace 'src/main/java/pro/erez/interstice/Interstice.java'
    $taskSources+=Join-Path $workspace 'src/main/resources/interstice.mixins.json'
    foreach($taskSource in $taskSources){$taskName=$taskSource.Substring($workspace.Length+1).Replace('\','/');$taskHashes[$taskName]=(Get-FileHash -LiteralPath $taskSource -Algorithm SHA256).Hash.ToLowerInvariant()}
    return $taskHashes
}
Push-Location -LiteralPath $workspace
try{
    $sourceBefore=Get-EitanSourceHashes
    $startedAt=Get-Date
    & "$env:JAVA_HOME/bin/java.exe" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain '-Pmod_version=0.7.0-preview.120-vehicle-fleet' "-PintersticeRunRoot=$target" "-PeitanPhase=$Phase" "-PprogressionPackSource=$workspace/.verification/tide-release-20261028/client" runStudioEitanDrive
    $code=$LASTEXITCODE
    $proof=Join-Path $profile "eitan-drive-$Phase.json"
    $fresh=(Test-Path -LiteralPath $proof) -and (Get-Item -LiteralPath $proof).LastWriteTime -ge $startedAt
    $sourceAfter=Get-EitanSourceHashes
    $sourceChanged=$sourceBefore.Count -ne $sourceAfter.Count -or @($sourceBefore.Keys | Where-Object {$sourceBefore[$_] -ne $sourceAfter[$_]}).Count -gt 0
    if($fresh){$report=Get-Content -Raw -LiteralPath $proof | ConvertFrom-Json;$report | Add-Member -NotePropertyName process_exit_code -NotePropertyValue $code -Force;$report | Add-Member -NotePropertyName runtime_source_sha256 -NotePropertyValue $sourceAfter -Force;$report | Add-Member -NotePropertyName source_changed_during_run -NotePropertyValue $sourceChanged -Force;$report | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $proof -Encoding utf8}
    if($sourceChanged){throw 'Eitan runtime sources changed during the native run; retain the report and repeat the affected check'}
    if($code -ne 0 -or -not $fresh -or -not $report.passed){throw 'Eitan drive did not pass; inspect isolated report/log'}
}finally{Pop-Location}
