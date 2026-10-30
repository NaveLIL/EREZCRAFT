package pro.erez.interstice.client;

/** Three equal 46 2/3 degree perspective views sampled from one 140 degree sensor.
 * Strip interpolation avoids the angular distortion of simply cutting a wide image.
 */
public final class EitanCameraOptics {
    public static final int STRIPS=32;
    public static final float ASPECT=(float)Math.tan(Math.toRadians(70));
    public record Sample(float u,float bottom,float top){}
    public static final Sample[][] SAMPLES=new Sample[3][STRIPS+1];
    static {for(int sector=0;sector<3;sector++)for(int i=0;i<=STRIPS;i++){
        double centre=Math.toRadians((sector-1)*140./3), k=(i*2./STRIPS-1)*Math.tan(Math.toRadians(70./3));
        double z=Math.cos(centre)-Math.sin(centre)*k, x=Math.sin(centre)+Math.cos(centre)*k;
        double half=Math.tan(Math.toRadians(70./3))/(.215/.15)/(2*z);
        SAMPLES[sector][i]=new Sample((float)(.5+x/(2*z*ASPECT)),(float)(.5-half),(float)(.5+half));
    }}
    private EitanCameraOptics(){}
}
