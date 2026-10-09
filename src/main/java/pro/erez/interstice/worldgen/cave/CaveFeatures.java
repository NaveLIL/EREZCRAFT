package pro.erez.interstice.worldgen.cave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.LevelReader;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.StoneVaults;
import pro.erez.interstice.worldgen.FeatureDistribution;
import pro.erez.interstice.ecology.CaveEcology;
import pro.erez.interstice.Interstice;

/** Decorations inspect actual enclosed air, never a guessed cave or a neighbor chunk. */
public final class CaveFeatures {
    private CaveFeatures() {}
    public record Counts(int floorPlants,int hangingPlants,int spireBlocks,int clingweed) {}
    private static long hash(long seed,int x,int y,int z) {
        long value=seed^x*341873128712L^z*132897987541L^y*0x723B9A1L;
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static int choice(long seed,BlockPos p,int bound) {return Math.floorMod(hash(seed,p.getX(),p.getY(),p.getZ()),bound);}
    public static boolean rock(BlockState state) {return StoneVaults.isGround(state)||state.getBlock() instanceof pro.erez.interstice.minerals.RiftOreBlock;}
    private static boolean inside(ChunkAccess chunk,BlockPos p) {
        return p.getX()>=chunk.getPos().getMinBlockX()&&p.getX()<=chunk.getPos().getMaxBlockX()
                &&p.getZ()>=chunk.getPos().getMinBlockZ()&&p.getZ()<=chunk.getPos().getMaxBlockZ()
                &&p.getY()>=chunk.getMinBuildHeight()&&p.getY()<chunk.getMaxBuildHeight();
    }
    /** Ceiling height, or -1 if open sky / an ocean / a plant is encountered. */
    public static int roof(GeometryProfile profile,ChunkAccess chunk,BlockPos floor,int maxHeight) {
        if(!rock(chunk.getBlockState(floor.below())))return -1;
        for(int dy=2;dy<=maxHeight&&floor.getY()+dy<profile.maxLand();dy++) {
            var state=chunk.getBlockState(floor.above(dy));
            if(rock(state))return floor.getY()+dy;
            if(!state.isAir())return -1;
        }
        return -1;
    }
    private static Block spire(ChunkAccess chunk,BlockPos pos) {
        return RealmBiomes.isVault(chunk,pos)?CaveMaterials.VAULT_SPIRE.get():RealmBiomes.isGarden(chunk,pos)?CaveMaterials.GARDEN_SPIRE.get():CaveMaterials.ASH_SPIRE.get();
    }
    /** Full validation before writes; tips never meet to block the passage between floor and roof. */
    public static int placeSpire(ChunkAccess chunk,BlockPos start,Direction tip,int length,Block block) {
        if(length<1||length>5||!inside(chunk,start))return 0;
        if(!rock(chunk.getBlockState(start.relative(tip.getOpposite()))))return 0;
        for(int i=0;i<length;i++) {
            var pos=start.relative(tip,i);
            if(!inside(chunk,pos)||!chunk.getBlockState(pos).isAir())return 0;
        }
        for(int i=0;i<length;i++) {
            DripstoneThickness thickness=i==length-1?DripstoneThickness.TIP:i==length-2?DripstoneThickness.FRUSTUM:i==0?DripstoneThickness.BASE:DripstoneThickness.MIDDLE;
            chunk.setBlockState(start.relative(tip,i),block.defaultBlockState().setValue(CaveMaterials.CaveSpireBlock.TIP_DIRECTION,tip).setValue(CaveMaterials.CaveSpireBlock.THICKNESS,thickness),false);
        }
        return length;
    }
    private static Direction growthFromRock(ChunkAccess chunk,BlockPos pos) {
        // Floor and ceiling colonies read as grass tufts and hanging tendrils; wall tufts
        // grow outward from the actual rock face, rather than outlining a cube of air.
        for(Direction direction:new Direction[]{Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}) {
            var support=pos.relative(direction);
            if(inside(chunk,support)&&rock(chunk.getBlockState(support)))return direction.getOpposite();
        }
        return null;
    }
    public static Counts decorate(GeometryProfile profile,ChunkAccess chunk,long seed,WorldGenRegion region) {
        return decorate(profile,chunk,seed,region,3);
    }
    /** Explicit revision opt-in; the old overload keeps exactly the pre-V4 population. */
    public static Counts decorate(GeometryProfile profile,ChunkAccess chunk,long seed,LevelReader region,int revision) {
        int plants=0,hanging=0,spires=0,clings=0;
        int startX=chunk.getPos().getMinBlockX(),startZ=chunk.getPos().getMinBlockZ();
        // Every position is eligible: no artificial decoration-free stripe at chunk borders.
        // Support reads are checked by growthFromRock before accessing this chunk.
        for(int x=startX;x<startX+16;x++)for(int z=startZ;z<startZ+16;z++) {
            // Revision-three grounded gardens can contain sealed, dry caves below the sea.
            // Actual air + rock + dry roof checks decide eligibility; flooded cavities fail.
            for(int y=profile.minY()+7;y<profile.maxLand()-5;y++) {
                var floor=new BlockPos(x,y,z);
                if(!chunk.getBlockState(floor).isAir()||!rock(chunk.getBlockState(floor.below())))continue;
                int ceiling=roof(profile,chunk,floor,64);
                if(ceiling<0)continue;
                int space=ceiling-y;
                if(space<3)continue;
                boolean vault=RealmBiomes.isVault(chunk,floor),garden=RealmBiomes.isGarden(chunk,floor);
                int roll=choice(seed^0x2B00A1L,floor,100);
                boolean colony=CaveDensity.colonized(seed,x,y+space/2,z);
                boolean lush=CaveDensity.lush(seed,x,y,z);
                boolean reservedFloor=false,reservedPendant=false;
                // Low exploratory passages (including the four-high mouth ramps) retain
                // full walking headroom. Colliding deposits belong to wider/taller rooms.
                if(roll<(vault?17:garden?9:6)&&space>=6) {
                    int limit=Math.min(vault?5:garden?3:2,Math.max(1,(space-2)/2));
                    int length=1+choice(seed^0x535A1L,floor,limit);
                    // Place independently at ceiling and floor, leaving >=2 blocks of central air.
                    var direction=(roll&1)==0?Direction.UP:Direction.DOWN;
                    var start=direction==Direction.UP?floor:floor.atY(ceiling-1);
                    spires+=placeSpire(chunk,start,direction,length,spire(chunk,floor));
                } else if(roll<(garden?47:vault?26:21) && lush && !colony) {
                    var state=(choice(seed^0x51A9L,floor,6)==0?CaveEcology.STING_FROND:CaveEcology.GLOW_BLOOM).get().defaultBlockState();
                    if(state.canSurvive(region,floor)){
                        reservedFloor=true;
                        if(FeatureDistribution.cavePlantAllowed(revision,seed,floor,false)){chunk.setBlockState(floor,state,false);plants++;}
                    }
                }
                var pendant=floor.atY(ceiling-1);
                if(roll>(garden?66:86)&&!colony&&chunk.getBlockState(pendant).isAir()) {
                    var state=CaveEcology.HANGING_GLOW_BLOOM.get().defaultBlockState();
                    if(state.canSurvive(region,pendant)){
                        reservedPendant=true;
                        if(FeatureDistribution.cavePlantAllowed(revision,seed,pendant,true)){chunk.setBlockState(pendant,state,false);hanging++;}
                    }
                }
                // Colonized rooms get a continuous skin of noncolliding toxic clingweed.
                // Other rooms have coherent patches; no random solid curtain obstructs the route.
                for(int at=y;at<ceiling;at++) {
                    var pos=floor.atY(at);
                    // Removing a flower must not expand a previously blocked clingweed pocket.
                    // Keep its former cell reserved so the colony remains identical to V3.
                    if(reservedFloor&&at==y||reservedPendant&&at==ceiling-1)continue;
                    if(!chunk.getBlockState(pos).isAir())continue;
                    boolean membrane=colony&&space<=6&&Math.floorMod(x+(int)(seed&63),64)==0;
                    Direction growth=growthFromRock(chunk,pos);
                    if(CaveDensity.colonized(seed,x,at,z)&&(growth!=null||membrane)) {
                        // The rare unsupported middle of a membrane hangs from its rocky roof.
                        var state=CaveEcology.CLINGWEED.get().defaultBlockState()
                                .setValue(BlockStateProperties.FACING,growth==null?Direction.DOWN:growth);
                        chunk.setBlockState(pos,state,false);clings++;
                    }
                }

                boolean isNest = Math.floorMod(hash(seed ^ 0x9B14E7L, x >> 3, 0, z >> 3), 11) == 0;
                // Archived revisions retain their original FULL decorations and future chunk edges.
                if (revision == 6 && isNest && !colony && space >= 3) {
                    int nestRoll = choice(seed ^ 0x7E3F1A9L, floor, 100);
                    if (chunk.getBlockState(floor).isAir() && rock(chunk.getBlockState(floor.below()))) {
                        if (nestRoll < 12) {
                            chunk.setBlockState(floor, Interstice.SPIDER_EGG_SAC.get().defaultBlockState().setValue(pro.erez.interstice.block.SpiderEggSacBlock.FACING, Direction.UP), false);
                        } else if (nestRoll < 35) {
                            chunk.setBlockState(floor, Interstice.RIFT_COBWEB.get().defaultBlockState(), false);
                        }
                    }
                    if (chunk.getBlockState(pendant).isAir() && rock(chunk.getBlockState(pendant.above()))) {
                        if (nestRoll > 88) {
                            chunk.setBlockState(pendant, Interstice.SPIDER_EGG_SAC.get().defaultBlockState().setValue(pro.erez.interstice.block.SpiderEggSacBlock.FACING, Direction.DOWN), false);
                        } else if (nestRoll > 60) {
                            chunk.setBlockState(pendant, Interstice.RIFT_COBWEB.get().defaultBlockState(), false);
                        }
                    }
                }
            }
        }
        return new Counts(plants,hanging,spires,clings);
    }
}
