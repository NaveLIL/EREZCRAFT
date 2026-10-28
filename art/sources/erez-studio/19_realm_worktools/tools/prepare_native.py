from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/wt01')
s=(ROOT.parent/'13_minecraft_armor/tools/gallery_java/ArmorPlayerGallery.java').read_text(encoding='utf-8');s=s[:s.index('    @SubscribeEvent public static void tick(ClientTickEvent.Post event)')]
s=s.replace('ArmorPlayerGallery','WorktoolsNative').replace('"gallery-"','"worktools-"').replace('armor-player-gallery.json','worktools-native.json').replace('armor-gallery','worktools-native')
start=s.index('    private static void equip(');end=s.index('    private static void reset(',start)
design=json.loads((ROOT/'source/worktools_WT03.json').read_text(encoding='utf-8'));names=list(design['models'])
equip='''    private static final String[] TOOLS=__NAMES__;
    private static final int[] QUADS=__QUADS__;
    private static int group,firstIndex;
    private static int current(int i){return (group*3+i)%TOOLS.length;}
    private static net.minecraft.world.item.Item tool(int i){return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,TOOLS[i]));}
    private static void equip(ServerPlayer p,String tier){int i=Arrays.asList(TIERS).indexOf(tier);for(var slot:RealmArmor.SLOTS)p.setItemSlot(slot,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(tool(current(i))));p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();}
'''.replace('__NAMES__','{'+','.join('"'+n+'"' for n in names)+'}').replace('__QUADS__','{'+','.join(str(len(d['model']['elements'])*6) for d in design['models'].values())+'}')
s=s[:start]+equip+s[end:]
s=s.replace('p.stopUsingItem();p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);','p.stopUsingItem();')
s+=(ROOT/'tools/native_tail.java.txt').read_text(encoding='utf-8')
(CAND/'src/smoke/java/pro/erez/interstice/client/WorktoolsNative.java').write_text(s,encoding='utf-8')
print('WT03 native fixture prepared')
