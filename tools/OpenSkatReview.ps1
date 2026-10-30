$ErrorActionPreference='Stop'
$taskRepoRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskRunRoot=Join-Path $taskRepoRoot '.verification/sk97n1'
$taskProfile=Join-Path $taskRunRoot 'studioSkat'
$taskProof=Get-Content -Raw -LiteralPath (Join-Path $taskProfile 'skat-reload.json') | ConvertFrom-Json
$taskDelivery=Get-Content -Raw -LiteralPath (Join-Path $taskRepoRoot 'docs/verification/SKAT_SK03.json') | ConvertFrom-Json
if(-not $taskProof.passed -or $taskProof.process_exit_code -ne 0 -or $taskProof.survival_cold_login_health -ne 20){throw 'Clean ship and Survival cold verification required'}
if($taskProof.version -ne $taskDelivery.candidate){throw 'Candidate differs from verified delivery'}
$taskJar=Join-Path $taskRepoRoot "build/libs/interstice-$($taskProof.version).jar"
if((Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash.ToLower() -ne $taskDelivery.jar_sha256){throw 'Verified ship artifact changed'}
$taskGames=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'cpw\.mods\.bootstraplauncher\.BootstrapLauncher|net\.minecraft\.client\.main\.Main|net\.neoforged\.devlaunch\.Main'})
if($taskGames.Count){Write-Output 'Minecraft is already open; no second client started.';return}
Set-Content -LiteralPath (Join-Path $taskProfile 'skat-owner-profile') -Value 'Owner review profile. Never use for automated tests.'
$env:JAVA_HOME=Join-Path $taskRepoRoot '.verification/toolchain/jdk-21.0.12.1+1'
Push-Location -LiteralPath $taskRepoRoot
try{
    & "$env:JAVA_HOME/bin/java.exe" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain "-Pmod_version=$($taskProof.version)" "-PintersticeRunRoot=$taskRunRoot" "-PprogressionPackSource=$taskRepoRoot/.verification/tide-release-20261028/client" '-PskatPhase=review' runStudioSkat
}finally{Pop-Location}
