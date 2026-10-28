from pathlib import Path
import shutil
ROOT=Path(__file__).resolve().parents[1];C=Path('D:/repos/Minecraft/EREZCRAFT/.verification/sk01')
shutil.copytree(ROOT/'source/compat',C/'src/avatarCompat',dirs_exist_ok=True)
p=C/'build.gradle';s=p.read_text(encoding='utf-8');marker='// EREZ avatar compatibility source set'
if marker not in s:
    s+='\n'+marker+'''
sourceSets { avatarCompat { compileClasspath += sourceSets.main.compileClasspath + files("D:/repos/Blender/catalog/custom_player_models/CustomPlayerModels-1.21-0.6.27a.jar") } }
neoForge.mods { erez_avatar_compat { sourceSet(sourceSets.avatarCompat) } }
tasks.register('avatarCompatJar',Jar) {
    dependsOn tasks.named('avatarCompatClasses')
    archiveBaseName='erez-avatar-compat';archiveVersion='0.1.0'
    from sourceSets.avatarCompat.output
    destinationDirectory=file('D:/repos/Blender/creations/17_rift_engineer_skin/exports')
}
'''
    p.write_text(s,encoding='utf-8')
print('Avatar compatibility source set prepared')
marker='// Load the exact packaged compatibility artifact in the owned client'
s=p.read_text(encoding='utf-8')
if marker not in s:
    s+='\n'+marker+'\ndependencies { runtimeOnly files("D:/repos/Blender/creations/17_rift_engineer_skin/exports/erez-avatar-compat-0.1.0.jar") }\n'
    p.write_text(s,encoding='utf-8')
