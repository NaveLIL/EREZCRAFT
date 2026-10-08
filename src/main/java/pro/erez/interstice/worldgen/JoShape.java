package pro.erez.interstice.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Adapted JoCode packing and fork/return semantics from DynamicTreesTeam/JoCode (MIT).
 * Copyright (c) 2025 DynamicTreesTeam. See META-INF/licenses/dynamic-trees-jocode.txt.
 * The bounded staging, path notation and endpoint handling below are project adaptations.
 */
public final class JoShape {
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final String PATH="DUNSWE[]";
    private JoShape() {}
    public record Skeleton(Map<BlockPos,Direction.Axis> logs,List<BlockPos> ends) {}
    public static String encodePath(String path) {
        if(path.isEmpty() || path.length()>512)throw new IllegalArgumentException("Tree path must contain 1..512 instructions");
        int depth=0;
        for(int i=0;i<path.length();i++) {
            char c=path.charAt(i);if(PATH.indexOf(c)<0)throw new IllegalArgumentException("Unknown tree path instruction "+c);
            if(c=='[' && ++depth>16)throw new IllegalArgumentException("Tree fork nesting exceeds 16");
            if(c==']' && --depth<0)throw new IllegalArgumentException("Unmatched tree return");
        }
        if(depth!=0)throw new IllegalArgumentException("Unclosed tree fork");
        String padded=(path.length()&1)==0?path:path+"]";StringBuilder result=new StringBuilder();
        for(int i=0;i<padded.length();i+=2)result.append(ALPHABET.charAt(PATH.indexOf(padded.charAt(i))*8+PATH.indexOf(padded.charAt(i+1))));
        return result.toString();
    }
    public static Skeleton draw(String encoded,BlockPos root,int turn,int extraHeight) {
        if(encoded.isEmpty() || encoded.length()>256 || turn<0 || turn>3 || extraHeight<0 || extraHeight>3)throw new IllegalArgumentException("Invalid bounded JoCode");
        Map<BlockPos,Direction.Axis> logs=new LinkedHashMap<>();List<BlockPos> ends=new ArrayList<>();var forks=new ArrayDeque<BlockPos>();
        BlockPos pos=root.below();
        for(int i=0;i<extraHeight;i++){pos=pos.above();logs.put(pos,Direction.Axis.Y);}
        boolean ended=false;
        for(int i=0;i<encoded.length()*2;i++) {
            int packed=ALPHABET.indexOf(encoded.charAt(i/2));if(packed<0)throw new IllegalArgumentException("Invalid JoCode character");
            int code=(i&1)==0?packed>>3:packed&7;
            if(ended){if(code==7&&i==encoded.length()*2-1)continue;throw new IllegalArgumentException("Instructions after top-level return");}
            if(code==6){if(forks.size()>=16)throw new IllegalArgumentException("Tree forks exceed 16");forks.push(pos);}
            else if(code==7){ends.add(pos);if(forks.isEmpty())ended=true;else pos=forks.pop();}
            else {
                Direction dir=Direction.from3DDataValue(code);
                if(dir.getAxis()!=Direction.Axis.Y)for(int rotation=0;rotation<turn;rotation++)dir=dir.getClockWise();
                pos=pos.relative(dir);
                if(pos.getY()<root.getY() || pos.getY()>root.getY()+20 || Math.abs(pos.getX()-root.getX())>7 || Math.abs(pos.getZ()-root.getZ())>7)
                    throw new IllegalArgumentException("JoCode leaves the checked tree envelope");
                logs.putIfAbsent(pos,dir.getAxis());
            }
        }
        if(!forks.isEmpty())throw new IllegalArgumentException("JoCode has an unclosed fork");
        // Dynamic Trees similarly identifies ends from the resulting branch network.
        ends.clear();
        for(var p:logs.keySet())if(!p.equals(root)) {
            int neighbors=0;for(var d:Direction.values())if(logs.containsKey(p.relative(d)))neighbors++;
            if(neighbors==1)ends.add(p);
        }
        return new Skeleton(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(logs)),List.copyOf(ends));
    }
}
