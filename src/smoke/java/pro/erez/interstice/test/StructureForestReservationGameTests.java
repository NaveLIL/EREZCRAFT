package pro.erez.interstice.test;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.ForestV5;
import pro.erez.interstice.worldgen.RealmStructuresV5;

/** Same immutable reservation for roots and every crown cell; no per-chunk clipping around ruins. */
@GameTestHolder("interstice_living")
@PrefixGameTestTemplate(false)
public final class StructureForestReservationGameTests {
    private static final long SEED=20261006L;
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private static ForestV5.GroundProbe flat(long seed,boolean reserve){
        var field=RealmStructuresV5.forestReservation(seed);
        return new ForestV5.GroundProbe(){
            public int surface(int x,int z){return 60;}
            public boolean solid(int x,int y,int z){return y<=60;}
            public boolean reserved(int x,int y,int z){return reserve&&field.test(x,z);}
        };
    }
    private static RealmStructuresV5.Slot selected(){
        for(int x=-4;x<0;x++)for(int z=-4;z<0;z++){var slot=RealmStructuresV5.slot(SEED,x,z);if(slot!=null)return slot;}
        throw new AssertionError("Missing negative-region structure candidate");
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void immutableReservationsKeepDefaultsAndUnrelatedForestUnchanged(GameTestHelper h){
        var field=RealmStructuresV5.forestReservation(SEED);var slot=selected();int x=slot.chunk().getMinBlockX(),z=slot.chunk().getMinBlockZ();
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)h.assertTrue(field.test(x+dx,z+dz),"Every possible template cell in a negative candidate chunk must be reserved");
        h.assertTrue(!field.test(x-1,z)&&!field.test(x+16,z),"The sparse field cannot reserve unrelated neighbouring chunks");
        RealmStructuresV5.forestReservation(SEED+(1L<<40)).test(x,z);
        h.assertTrue(field.test(x,z)==RealmStructuresV5.forestReserved(SEED,x,z),"Another seed or query order changed a reservation");
        var defaultProbe=new ForestV5.GroundProbe(){public int surface(int bx,int bz){return 60;}public boolean solid(int bx,int y,int bz){return y<=60;}};
        var baseline=ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),defaultProbe);
        h.assertTrue(baseline.equals(ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),flat(SEED,false))),"Default-false GroundProbe changed the existing forest budget");
        h.assertTrue(baseline.equals(ForestV5.plan(PROFILE,0,0,SEED,h.getLevel(),flat(SEED,true))),"Far from a rare reservation, species, roots and exact forest cells must stay unchanged");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void crownsCrossingReservedSeamsAreRejectedAsWholeTreesInEitherOrder(GameTestHelper h){
        RealmStructuresV5.Slot selected=null;ForestV5.Root crossing=null;
        for(int x=-4;x<0&&crossing==null;x++)for(int z=-4;z<0&&crossing==null;z++){
            var slot=RealmStructuresV5.slot(SEED,x,z);if(slot==null)continue;
            var ordinary=ForestV5.plan(PROFILE,slot.chunk().x,slot.chunk().z,SEED,h.getLevel(),flat(SEED,false));
            for(var root:ordinary.roots())if((root.pos().getX()>>4)!=slot.chunk().x||(root.pos().getZ()>>4)!=slot.chunk().z){selected=slot;crossing=root;break;}
        }
        h.assertTrue(selected!=null&&crossing!=null,"Fixture requires a natural cross-chunk crown entering a real reserved chunk");
        int cx=selected.chunk().x,cz=selected.chunk().z;long rejectedKey=crossing.key();
        var pieces=new HashMap<String,ForestV5.Plan>();var first=new HashMap<BlockPos,BlockState>();int retained=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var plan=ForestV5.plan(PROFILE,cx+dx,cz+dz,SEED,h.getLevel(),flat(SEED,true));pieces.put(dx+":"+dz,plan);first.putAll(plan.cells());retained+=plan.roots().size();
            h.assertTrue(plan.roots().stream().noneMatch(root->root.key()==rejectedKey),"A reserved canopy was only clipped in one chunk, leaving its stem or leaves elsewhere");
            h.assertTrue(plan.cells().keySet().stream().noneMatch(pos->RealmStructuresV5.forestReserved(SEED,pos.getX(),pos.getZ())),"A neighbouring crown wrote cells into a reserved template volume");
        }
        h.assertTrue(retained>0,"A sparse structure clearing cannot remove the surrounding forest");
        var reverse=new HashMap<BlockPos,BlockState>();
        for(int dx=1;dx>=-1;dx--)for(int dz=1;dz>=-1;dz--){
            var plan=ForestV5.plan(PROFILE,cx+dx,cz+dz,SEED,h.getLevel(),flat(SEED,true));
            h.assertTrue(plan.equals(pieces.get(dx+":"+dz)),"Reservation rejection depends on decorated neighbour or generation order");reverse.putAll(plan.cells());
        }
        h.assertTrue(first.equals(reverse),"Reverse planning changed surviving complete forest cells");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void realTemplateKeepsItsLootAndClearSpaceBeforeOrAfterReservedForestPlans(GameTestHelper h){
        var slot=selected();var chunk=new ProtoChunk(slot.chunk(),UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlockState(new BlockPos(slot.chunk().getMinBlockX()+x,60,slot.chunk().getMinBlockZ()+z),Interstice.ABYSSAL_TURF.get().defaultBlockState(),false);
        var before=ForestV5.plan(PROFILE,slot.chunk().x,slot.chunk().z,SEED,h.getLevel(),flat(SEED,true));
        h.assertTrue(before.cells().isEmpty(),"Reserved forest must leave the complete candidate footprint clear");
        h.assertTrue(RealmStructuresV5.generate(PROFILE,chunk,SEED,h.getLevel().getStructureManager(),h.getLevel().registryAccess(),35),"A real authored template must still fit after immutable forest planning");
        var tags=new HashMap<BlockPos,net.minecraft.nbt.CompoundTag>();
        for(var pos:chunk.getBlockEntitiesPos()){var tag=chunk.getBlockEntityNbt(pos);if(tag!=null)tags.put(pos,tag.copy());}
        var after=ForestV5.plan(PROFILE,slot.chunk().x,slot.chunk().z,SEED,h.getLevel(),flat(SEED,true));
        h.assertTrue(before.equals(after)&&after.cells().isEmpty(),"A placed template changed the immutable tree decision");
        h.assertTrue(!tags.isEmpty(),"The real template requires its actual loot containers");
        for(var entry:tags.entrySet())h.assertTrue(entry.getValue().equals(chunk.getBlockEntityNbt(entry.getKey())),"Reservation planning mutated a generated loot container");
        h.succeed();
    }
}
