package pro.erez.interstice.test;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.*;
import pro.erez.interstice.worldgen.*;
import pro.erez.interstice.worldgen.terrain.*;

@GameTestHolder("interstice_mining")
@PrefixGameTestTemplate(false)
public final class RealmSurfaceGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void mineralPowderActuallySinksButBootsSupportAndDustDoesNotFreeze(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,3,4));var block=MineralEcology.MINERAL_POWDER.get();var state=block.defaultBlockState();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            level.setBlock(p.offset(dx,-2,dz),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(p.offset(dx,-1,dz),state,3);level.setBlock(p.offset(dx,0,dz),state,3);
        }
        var player=TestPlayers.create(h,new BlockPos(4,5,4),GameType.SURVIVAL);
        try{
            h.assertTrue(state.getCollisionShape(level,p,CollisionContext.of(player)).isEmpty(),"Loose mineral dust behaves as solid stone");
            player.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.LEATHER_BOOTS));
            h.assertTrue(!state.getCollisionShape(level,p,CollisionContext.of(player)).isEmpty(),"Suitable boots cannot support weight on the powder");
            block.entityInside(state,level,p,player);
            h.assertTrue(player.hasEffect(MobEffects.WEAKNESS)&&player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&player.getTicksFrozen()==0,"Mineral dust lacks its own bounded debuffs or copies hypothermia");
            h.assertTrue(block.pickupBlock(player,level,p,state).isEmpty()&&level.getBlockState(p).is(block),"Mineral dust turns into vanilla powder-snow buckets");
            h.assertTrue(new ItemStack(Items.IRON_SHOVEL).isCorrectToolForDrops(state),"Loose mineral dust has no appropriate gathering tool");
        }finally{TestPlayers.remove(player);}
        var item=new ItemEntity(level,p.getX()+.5,p.getY()+2,p.getZ()+.5,new ItemStack(Items.IRON_NUGGET));
        // ItemEntity's constructor launches items sideways. A controlled vertical-drop fixture
        // removes that random launch; all subsequent gravity, collisions and dust contacts are native.
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);item.setNeverPickUp();level.addFreshEntity(item);
        h.runAfterDelay(40,()->{try{
            String observed="alive="+item.isAlive()+" ticks="+item.tickCount+" pos="+item.position()+" expectedY="+(p.getY()-1)+".."+(p.getY()+.9)+" velocity="+item.getDeltaMovement()+" fallDistance="+item.fallDistance;
            System.out.println("MINERAL_POWDER_NATIVE_DROP "+observed);
            h.assertTrue(item.isAlive()&&item.getY()<p.getY()+.9&&item.getY()>=p.getY()-1,"Actual entity movement did not sink into the bounded powder pocket: "+observed);h.succeed();
        }finally{item.discard();}});
    }
    private static ProtoChunk plateau(GameTestHelper h,int top){
        var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        var biome=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(RealmBiomes.STONE_VAULTS);
        chunk.fillBiomesFromNoise((x,y,z,s)->biome,Climate.empty());chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=1;y<=top;y++)chunk.setBlockState(new BlockPos(x,y,z),(y==top?Interstice.ABYSSAL_TURF.get():VaultMaterials.VAULTSTONE.get()).defaultBlockState(),false);
        return chunk;
    }
    private static TerrainColumn column(double height,boolean mountain){return new TerrainColumn(){
        public TerrainV2.Weights weights(){return mountain?new TerrainV2.Weights(0,0,1):new TerrainV2.Weights(0,1,0);}
        public TerrainV2.Kind dominant(){return mountain?TerrainV2.Kind.STONE_VAULTS:TerrainV2.Kind.PALE_GARDENS;}
        public double gardenHeight(){return height;}public double vaultHeight(){return height;}public double maximumSurfaceY(){return 198;}public double density(double y){return (height-y)/12;}
    };}
    @GameTest(template="empty",timeoutTicks=100)
    public static void shoresAndAlpineVeneersUseNaturalSupportWithoutReplacingAir(GameTestHelper h){
        var beach=plateau(h,35);var b=RealmSurfaces.decorate(GeometryProfile.TALL,beach,20261006,(x,z)->column(x<8?35.5:33.5,false));
        h.assertTrue(b.sand()>0&&beach.getBlockState(new BlockPos(4,35,4)).is(MineralEcology.TOXIC_SAND.get()),"A real lower-sea shore has no toxic beach sand");
        h.assertTrue(beach.getBlockState(new BlockPos(4,5,4)).is(VaultMaterials.VAULTSTONE.get()),"Coastal veneer rewrote the foundation");
        var mountain=plateau(h,160);var m=RealmSurfaces.decorate(GeometryProfile.TALL,mountain,20261006,(x,z)->column(160.5,true));
        h.assertTrue(m.bareRock()>0&&m.frost()+m.powder()>0,"High mountain turf did not become an alpine rock/frost surface");
        h.assertTrue(mountain.getBlockState(new BlockPos(7,170,7)).isAir(),"Alpine material created a floating structure above the surface");
        var pos=h.absolutePos(new BlockPos(4,3,4));h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(pos,MineralEcology.MINERAL_FROST.get().defaultBlockState(),3);
        MineralEcology.MINERAL_FROST.get().randomTick(h.getLevel().getBlockState(pos),h.getLevel(),pos,h.getLevel().random);
        h.assertTrue(h.getLevel().getBlockState(pos).is(MineralEcology.MINERAL_FROST.get()),"Mineral deposits melt as terrestrial snow");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void livingTorchAndHarvestedBudUseOriginalThreeDimensionalFlowerHead(GameTestHelper h){
        try{
            var loader=RealmSurfaceGameTests.class.getClassLoader();
            var original=com.google.gson.JsonParser.parseString(new String(loader.getResourceAsStream("assets/interstice/models/block/tide_sprout_flower.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(String name:List.of("living_torch","living_wall_torch","luminous_bud")){
                var model=com.google.gson.JsonParser.parseString(new String(loader.getResourceAsStream("assets/interstice/models/block/minerals/"+name+".json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var textures=model.getAsJsonObject("textures");
                h.assertTrue(textures.get("core").equals(original.getAsJsonObject("textures").get("core"))&&textures.get("petal").equals(original.getAsJsonObject("textures").get("petal")),"Living torch invented another plant's head texture");
                for(var value:model.getAsJsonArray("elements")){var e=value.getAsJsonObject();var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");
                    h.assertTrue(to.get(0).getAsDouble()>from.get(0).getAsDouble()&&to.get(1).getAsDouble()>from.get(1).getAsDouble()&&to.get(2).getAsDouble()>from.get(2).getAsDouble(),"Living head is still a crossed flat sprite");}
            }
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}h.succeed();
    }
}
