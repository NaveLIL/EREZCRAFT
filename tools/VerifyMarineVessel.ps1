param([string]$RunRoot='D:/repos/Minecraft/EREZCRAFT/.verification/ne01h01')
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$allowed=(Resolve-Path -LiteralPath (Join-Path $workspace '.verification')).Path
$parent=(Resolve-Path -LiteralPath (Split-Path -Parent $RunRoot)).Path
if($parent -ne $allowed -or (Split-Path -Leaf $RunRoot) -notmatch '^ne01h[A-Za-z0-9_-]+$'){throw 'Disposable NE01 hull root required'}
$games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'net\.neoforged\.devlaunch\.Main|cpw\.mods\.bootstraplauncher\.BootstrapLauncher|net\.minecraft\.client\.main\.Main'})
if($games.Count){throw "Minecraft already running (PID $($games.ProcessId -join ', ')); no second client"}
if(Test-Path -LiteralPath $RunRoot){throw 'New disposable hull profile required'}
New-Item -ItemType Directory -Path $RunRoot | Out-Null
$env:JAVA_HOME=Join-Path $workspace '.verification/toolchain/jdk-21.0.12.1+1'
Push-Location -LiteralPath $workspace
try {
 & "$env:JAVA_HOME/bin/java.exe" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain '-Pmod_version=0.7.0-preview.102-ne01' "-PintersticeRunRoot=$RunRoot" "-PprogressionPackSource=$workspace/.verification/tide-release-20261028/client" '-PmarinePhase=vessel' runStudioMarineAe2
 $code=$LASTEXITCODE
 $file=Join-Path $RunRoot 'studioMarineAe2/marine-vessel.json'
 if(-not(Test-Path -LiteralPath $file)){throw 'No native hull evidence'}
 $proof=Get-Content -Raw -LiteralPath $file | ConvertFrom-Json
 $proof | Add-Member -NotePropertyName process_exit_code -NotePropertyValue $code -Force
 $proof | ConvertTo-Json -Depth 30 | Set-Content -Encoding utf8 -LiteralPath $file
 if($code -ne 0 -or -not $proof.passed){throw 'NE01 native hull check failed; inspect owned logs'}
}finally{Pop-Location}
