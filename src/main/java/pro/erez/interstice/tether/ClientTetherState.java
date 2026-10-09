package pro.erez.interstice.tether;

import java.util.*;

/** Render cache contains no server-authority setters and is bounded independently of packet frequency. */
public final class ClientTetherState {
    private static final Map<Integer,TetherNetworking.State> LINKS=new LinkedHashMap<>();
    public static void apply(TetherNetworking.State state){if(!state.active()){LINKS.remove(state.entityId());return;}
        if(state.length()<8||state.length()>48||state.facing()<2||state.facing()>5)return;
        if(LINKS.size()>=128&&!LINKS.containsKey(state.entityId()))LINKS.remove(LINKS.keySet().iterator().next());LINKS.put(state.entityId(),state);
    }
    public static List<TetherNetworking.State> links(){return List.copyOf(LINKS.values());}
    public static void clear(){LINKS.clear();}
    private ClientTetherState(){}
}
