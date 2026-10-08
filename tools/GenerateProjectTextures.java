import java.awt.Color;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Original, editable 16x16 project artwork. No source PNGs or external artwork are read. */
public final class GenerateProjectTextures {
    static final Path ROOT=Path.of("src/main/resources/assets/interstice/textures");
    static BufferedImage image(){return new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);}
    static int hash(int x,int y,int seed){int n=x*374761393+y*668265263+seed*1274126177;n=(n^(n>>>13))*1274126177;return n^(n>>>16);}
    static double noise(int x,int y,int seed){return (hash(Math.floorMod(x,16),Math.floorMod(y,16),seed)&65535)/65535.0;}
    static int color(int r,int g,int b,double light){return 0xff000000|(clamp(r*light)<<16)|(clamp(g*light)<<8)|clamp(b*light);}
    static int clamp(double n){return Math.max(0,Math.min(255,(int)Math.round(n)));}
    static void seams(BufferedImage im,boolean vertical){for(int y=0;y<16;y++)im.setRGB(15,y,im.getRGB(0,y));if(vertical)for(int x=0;x<16;x++)im.setRGB(x,15,im.getRGB(x,0));}
    static void save(BufferedImage im,String kind,String name)throws Exception{Path p=ROOT.resolve(kind).resolve(name+".png");Files.createDirectories(p.getParent());ImageIO.write(im,"PNG",p.toFile());}
    static BufferedImage stone(){BufferedImage im=image();for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        int band=y/4,shift=(band&1)*3;double grain=.81+noise(x,y,14)*.38;
        int joint=Math.floorMod(x+shift,8);if(joint==0 && y%4!=0)grain*=.74;if(y%4==2)grain*=1.14;
        im.setRGB(x,y,color(46,41,60,grain));
    }seams(im,true);return im;}
    static BufferedImage turf(){BufferedImage im=image();for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        double n=.88+noise(x,y,39)*.24;if((hash(x/2,y/2,83)&7)==0)n+=.09;
        im.setRGB(x,y,color(107,92,103,n));
    }seams(im,true);return im;}
    static BufferedImage bark(boolean stripped){BufferedImage im=image();for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        double ridge=.82+.12*Math.sin(x*Math.PI/2)+noise(x,y,75)*.25;
        if(!stripped && (x==3||x==8||x==12))ridge*=.70;
        im.setRGB(x,y,stripped?color(64,55,69,ridge):color(38,33,46,ridge));
    }seams(im,true);return im;}
    static BufferedImage end(boolean stripped){BufferedImage im=image();for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        double dx=x-7.5,dy=y-7.5,rad=Math.max(Math.abs(dx),Math.abs(dy));
        double n=.91+noise(x,y,118)*.18;if(((int)(Math.hypot(dx,dy)*1.2)&1)==0)n*=.84;
        im.setRGB(x,y,rad>5.5?color(stripped?64:38,stripped?55:33,stripped?69:46,n):color(77,65,80,n));
    }seams(im,true);return im;}
    static BufferedImage planks(){BufferedImage im=image();for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        double n=.88+noise(x,y,201)*.22;if(y==4||y==9||y==13)n*=.62;
        if(x==Math.floorMod(y/5*7+3,16)&&y%5<2)n*=.78;
        im.setRGB(x,y,color(35,29,35,n));
    }seams(im,true);return im;}
    static BufferedImage leaves(){BufferedImage im=image();for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        boolean hole=noise(x,y,312)<.32 && noise(x/2,y/2,324)<.84;
        if(!hole)im.setRGB(x,y,color(94,73,87,.77+noise(x,y,302)*.42));
    }
    for(int[] p:new int[][]{{3,4},{11,5},{6,11},{12,12}})im.setRGB(p[0],p[1],0xff55b3a6);
    seams(im,true);return im;}
    static BufferedImage facets(int r,int g,int b,int seed){BufferedImage im=image();for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        int cell=Math.floorMod(x+2*y,7);double n=.78+noise(x/3,y/3,seed)*.34;
        if(cell==0||cell==1)n+=.15;if(cell==5)n-=.12;
        im.setRGB(x,y,color(r,g,b,n));
    }seams(im,true);return im;}
    static BufferedImage icon(String type){BufferedImage im=image();var g=im.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        if(type.equals("rift_lens")){
            g.setColor(new Color(0x32283f));g.fillPolygon(new Polygon(new int[]{2,6,11,13,10,5},new int[]{11,3,1,6,12,15},6));
            g.setColor(new Color(0x79649b));g.fillPolygon(new Polygon(new int[]{4,7,10,11,8,5},new int[]{10,4,3,7,11,13},6));
            g.setColor(new Color(0x91cfc4));g.fillPolygon(new Polygon(new int[]{6,8,10,8},new int[]{9,4,5,10},4));g.setColor(new Color(0xc0e2d6));g.fillRect(8,4,1,3);
        }else if(type.equals("wayfarer_key")){
            g.setColor(new Color(0x433226));g.drawLine(3,13,11,5);g.drawLine(4,13,12,5);g.fillRect(8,2,6,6);
            g.setColor(new Color(0xa67f50));g.drawLine(4,12,11,5);g.fillRect(9,3,4,4);g.fillRect(2,11,3,3);
            g.setColor(new Color(0xe0b779));g.drawLine(4,11,10,5);g.fillRect(9,3,3,1);g.setColor(new Color(0x65bcaf));g.fillRect(10,4,2,2);
        }else{
            for(int y=2;y<14;y++)for(int x=2;x<14;x++){
                double rad=Math.hypot(x-7.5,y-7.5);if(rad>5.7)continue;
                double n=1.1-.045*(x+y);im.setRGB(x,y,rad>4.4?color(83,68,105,n):color(185,171,213,n));
            }
            g.setColor(new Color(0x5a4b71));g.drawRect(6,5,4,5);g.setColor(new Color(0xdccff2));g.fillRect(5,4,3,2);
        }g.dispose();return im;}
    public static void main(String[] args)throws Exception{
        BufferedImage stone=stone(),top=turf(),side=image(),ore=stone(),leaf=leaves();
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int fringe=6+(hash(x,0,66)&3);side.setRGB(x,y,y<=fringe?top.getRGB(x,Math.min(y,15)):stone.getRGB(x,y));
        }seams(side,false);
        for(int[] nugget:new int[][]{{3,3},{10,2},{6,8},{12,10},{2,12},{8,13},{1,7}})for(int dy=0;dy<2;dy++)for(int dx=0;dx<3;dx++){
            int x=nugget[0]+dx,y=nugget[1]+dy;double n=1.1-dx*.12-dy*.25;ore.setRGB(x,y,color(180,167,204,n));
        }seams(ore,true);
        save(stone,"block","riftstone");save(top,"block","abyssal_turf_top");save(side,"block","abyssal_turf_side");save(ore,"block","riftsilver_ore");
        save(bark(false),"block","gloomcrown_log");save(end(false),"block","gloomcrown_log_top");save(bark(true),"block","stripped_gloomcrown_log");save(end(true),"block","stripped_gloomcrown_log_top");save(planks(),"block","gloomcrown_planks");save(leaf,"block","gloomcrown_leaves");
        save(facets(83,70,85,407),"block","tide_sprout_petal");save(facets(60,125,116,419),"block","tide_sprout_core");save(bark(true),"block","tide_sprout_stalk");
        BufferedImage frame=image();for(int y=0;y<16;y++)for(int x=0;x<16;x++)frame.setRGB(x,y,x<2||x>13||y<2||y>13?ore.getRGB(x,y):stone.getRGB(x,y));save(frame,"block","rift_frame");
        BufferedImage portal=new BufferedImage(16,128,BufferedImage.TYPE_INT_ARGB);
        for(int phase=0;phase<8;phase++)for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            double wave=Math.sin((x+y*.7)*.5+phase*Math.PI/4),light=.58+noise(x+phase,y+phase*2,501)*.42;
            int rgb=wave>.65?color(188,179,115,light):wave<-.65?color(125,44,61,light):color(88,70,112,light);
            portal.setRGB(x,phase*16+y,(188<<24)|(rgb&0xffffff));
        }save(portal,"block","rift_portal");
        for(String name:new String[]{"rift_lens","wayfarer_key","pressure_coupler"})save(icon(name),"item",name);
        Path preview=Path.of("art/generated/project-textures/texture-sheet.png");Files.createDirectories(preview.getParent());
        BufferedImage sheet=new BufferedImage(960,480,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();g.setColor(new Color(0x17141c));g.fillRect(0,0,960,480);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        BufferedImage[] tiles={stone,top,side,ore,bark(false),planks(),leaf,facets(60,125,116,419),icon("rift_lens"),icon("wayfarer_key"),icon("pressure_coupler"),frame};
        for(int i=0;i<tiles.length;i++){int x=(i%6)*160,y=(i/6)*240;g.drawImage(tiles[i],x+16,y+24,128,128,null);if(i<4){g.drawImage(tiles[i],x+16,y+170,48,48,null);g.drawImage(tiles[i],x+64,y+170,48,48,null);g.drawImage(tiles[i],x+112,y+170,32,48,null);}}
        g.dispose();ImageIO.write(sheet,"PNG",preview.toFile());System.out.println("Generated 18 original project textures without reading any external images.");
    }
}
