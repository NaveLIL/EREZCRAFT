param([Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Trial,
      [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label)
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$isolated=Join-Path $workspace '.verification/skat-thrust-isolated'
$run=Join-Path $isolated ".verification/$Trial"
$python='C:/Users/Dimon/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$lease=Join-Path $PSScriptRoot 'SkatTrialLease.py'
$deadline=(Get-Date).AddMinutes(20)
$attempt=0
Push-Location -LiteralPath $isolated
try {
 while($true){
  $games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'net\.neoforged\.devlaunch\.Main|cpw\.mods\.bootstraplauncher\.BootstrapLauncher|net\.minecraft\.client\.main\.Main'})
  if($games.Count){
   if((Get-Date) -gt $deadline){throw 'Existing game still active; Skat trial never started'}
   Start-Sleep -Seconds 5
   continue
  }
  & $python $lease acquire $PID
  if($LASTEXITCODE -ne 0){throw 'Native slot reservation failed'}
  $attempt++
  $log=Join-Path $workspace ".verification/skat-20261029/thrust-$Label-attempt$attempt.log"
  try {
   ./tools/VerifySkat.ps1 -Phase thrust-create -RunRoot $run *> $log
   break
  } catch {
   $reason=$_.Exception.Message
   $body=if(Test-Path -LiteralPath $log){Get-Content -Raw -LiteralPath $log}else{''}
   if(-not(Test-Path -LiteralPath (Join-Path $run 'studioSkat/saves')) -and ($reason -match 'existing Minecraft|Existing game' -or $body -match 'Existing game PID')){
    Add-Content -Encoding utf8 -LiteralPath $log -Value $reason
    & $python $lease release $PID
    if((Get-Date) -gt $deadline){throw}
    Start-Sleep -Seconds 5
    continue
   }
   throw
  }
 }
} finally {
 & $python $lease release $PID
 Pop-Location
}
