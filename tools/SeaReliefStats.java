import pro.erez.interstice.SeaSurface;

public class SeaReliefStats {
    public static void main(String[] args) {
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int x=-16;x<=17;x++) for(int z=-16;z<=17;z++) {
            double value=SeaSurface.vertexHeight(x,z);
            min=Math.min(min,value);max=Math.max(max,value);
        }
        System.out.println("{\"minimum_y\":"+min+",\"maximum_y\":"+max+",\"actual_relief\":"+(max-min)+",\"allowed_minimum_y\":84,\"allowed_maximum_y\":96}");
    }
}
