from pathlib import Path
import subprocess,os,json
ROOT=Path(__file__).resolve().parents[1]
JDK=Path('D:/repos/Minecraft/EREZCRAFT/.verification/toolchain/jdk-21.0.12.1+1/bin')
cache=Path('C:/Users/Dimon/.gradle/caches/modules-2/files-2.1')
jars=[Path('D:/repos/Blender/catalog/custom_player_models/CustomPlayerModels-1.21-0.6.27a.jar')]
for relative in ['com.google.code.gson/gson/2.10.1','com.google.guava/guava','org.apache.commons/commons-lang3']:
    candidates=list((cache/relative).rglob('*.jar'));jars+=candidates[:1]
classes=ROOT/'reports/compiler_classes';classes.mkdir(exist_ok=True)
cp=os.pathsep.join(str(p) for p in jars)
generated=ROOT/'reports/editor_adapters';generated.mkdir(exist_ok=True)
commands=[[str(JDK/'javac.exe'),'-J-Xmx128m','-cp',cp,'-d',str(classes),str(ROOT/'tools/GenerateAdapters.java')],
          [str(JDK/'java.exe'),'-Xmx128m','-cp',str(classes)+os.pathsep+cp,'GenerateAdapters',str(generated)],
          [str(JDK/'javac.exe'),'-J-Xmx256m','-cp',cp,'-d',str(classes),str(ROOT/'tools/CompileSkin.java')]+[str(generated/(n+'.java')) for n in ['HeadlessBlockHandler','HeadlessItemHandler','HeadlessEntityHandler','HeadlessBiomeHandler']],
          [str(JDK/'java.exe'),'-Xmx384m','-Djava.awt.headless=true','-cp',str(classes)+os.pathsep+cp,'CompileSkin',str(ROOT)]]
for command in commands:
    completed=subprocess.run(command,capture_output=True,text=True,encoding='utf8',errors='replace')
    print(completed.stdout);print(completed.stderr)
    if completed.returncode:raise SystemExit(completed.returncode)
