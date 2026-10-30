$ErrorActionPreference='Stop'
$taskRepoRoot=Split-Path -Parent $PSScriptRoot
$taskProfileRoot=Join-Path $taskRepoRoot '.verification/oc48n2'
$taskProof=Join-Path $taskProfileRoot 'studioLowerOceanDh/ocean-dh.json'
if(-not (Test-Path -LiteralPath $taskProof)){throw 'Verified OC01 + DH scene not found'}
if(-not (Get-Content -Raw -LiteralPath $taskProof | ConvertFrom-Json).passed){throw 'OC01 + DH verification did not pass'}
$taskGames=Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'cpw.mods.bootstraplauncher.BootstrapLauncher|net.minecraft.client.main.Main|net.neoforged.devlaunch.Main'}
if($taskGames){Write-Output 'A Minecraft client is already running. Use its window; a second client was not started.';return}
Set-Content -LiteralPath (Join-Path $taskProfileRoot 'studioLowerOceanDh/ocean-dh-owner-profile') -Value 'Owner review profile; never use for automated tests.'
$taskJdk=Join-Path $taskRepoRoot '.verification/toolchain/jdk-21.0.12.1+1'
$env:JAVA_HOME=$taskJdk
$env:PATH=(Join-Path $taskJdk 'bin')+[IO.Path]::PathSeparator+$env:PATH
$taskJava=Join-Path $taskJdk 'bin/java.exe'
$taskWrapper=Join-Path $taskRepoRoot 'gradle/wrapper/gradle-wrapper.jar'
$taskPack=Join-Path $taskRepoRoot '.verification/tide-release-20261028/client'
Push-Location -LiteralPath $taskRepoRoot
try { & $taskJava -classpath $taskWrapper org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain '-Pmod_version=0.7.0-preview.49-ocean-dh-neoforge' "-PintersticeRunRoot=$taskProfileRoot" "-PprogressionPackSource=$taskPack" '-PoceanDhReview=true' runStudioLowerOceanDh }
finally { Pop-Location }
