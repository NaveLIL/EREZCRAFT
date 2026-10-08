import java.awt.Color;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Original project artwork plus the explicitly documented CC0 geological library. */
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
        GeologyLibrary.generate();
        stone=ImageIO.read(ROOT.resolve("block/riftstone.png").toFile());side=ImageIO.read(ROOT.resolve("block/abyssal_turf_side.png").toFile());ore=ImageIO.read(ROOT.resolve("block/riftsilver_ore.png").toFile());frame=ImageIO.read(ROOT.resolve("block/rift_frame.png").toFile());
        Path preview=Path.of("art/generated/project-textures/texture-sheet.png");Files.createDirectories(preview.getParent());
        BufferedImage sheet=new BufferedImage(960,480,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();g.setColor(new Color(0x17141c));g.fillRect(0,0,960,480);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        BufferedImage[] tiles={stone,top,side,ore,bark(false),planks(),leaf,facets(60,125,116,419),icon("rift_lens"),icon("wayfarer_key"),icon("pressure_coupler"),frame};
        for(int i=0;i<tiles.length;i++){int x=(i%6)*160,y=(i/6)*240;g.drawImage(tiles[i],x+16,y+24,128,128,null);if(i<4){g.drawImage(tiles[i],x+16,y+170,48,48,null);g.drawImage(tiles[i],x+64,y+170,48,48,null);g.drawImage(tiles[i],x+112,y+170,32,48,null);}}
        g.dispose();ImageIO.write(sheet,"PNG",preview.toFile());
        System.out.println("Generated original flora/items and documented CC0 geology. Oceans are unchanged.");
    }
}

/** Small verified sources are kept in the repository; generation needs neither network nor research files. */
final class GeologyLibrary {
    static BufferedImage source(String name,String expected)throws Exception{
        Path p=Path.of("art/sources/cc0/block-texture-set/"+name+".png");
        String actual=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
        if(!actual.equals(expected))throw new IllegalStateException("CC0 source changed: "+p);
        BufferedImage im=ImageIO.read(p.toFile());if(im.getWidth()!=16||im.getHeight()!=16)throw new IllegalStateException("Expected 16x16 CC0 tile");return im;
    }
    static BufferedImage tint(BufferedImage src,int r,int g,int b){
        double mean=0;for(int y=0;y<16;y++)for(int x=0;x<16;x++){int c=src.getRGB(x,y);mean+=((c>>16)&255)*.2126+((c>>8)&255)*.7152+(c&255)*.0722;}mean/=256;
        BufferedImage out=GenerateProjectTextures.image();for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int c=src.getRGB(x,y);double l=(((c>>16)&255)*.2126+((c>>8)&255)*.7152+(c&255)*.0722)/mean;
            out.setRGB(x,y,GenerateProjectTextures.color(r,g,b,.48+.52*l));
        }GenerateProjectTextures.seams(out,true);return out;
    }
    static void preserveEdges(BufferedImage out,BufferedImage original){for(int i=0;i<16;i++){
        out.setRGB(0,i,original.getRGB(0,i));out.setRGB(15,i,original.getRGB(15,i));out.setRGB(i,0,original.getRGB(i,0));out.setRGB(i,15,original.getRGB(i,15));
    }}
    static BufferedImage copy(BufferedImage source){BufferedImage out=GenerateProjectTextures.image();var g=out.createGraphics();g.drawImage(source,0,0,null);g.dispose();return out;}
    static BufferedImage variation(BufferedImage source,int variant){
        BufferedImage out=copy(source);for(int y=1;y<15;y++)for(int x=1;x<15;x++){
            int sx=variant==1?15-x:y,sy=variant==1?y:15-x;out.setRGB(x,y,source.getRGB(sx,sy));
        }preserveEdges(out,source);return out;
    }
    static void generate()throws Exception{
        var basalt=source("basalt","18d71f03bc9427ec8d0c01becef67d6cf509f9873fcd07f1f8928e1c4ac9969f");
        var schist=source("schist","a2fec5b29c6e21646efe83266f283628ac49eb9bf45cbb40916630ded39db708");
        var vault=tint(schist,74,62,79);var weathered=tint(schist,103,87,94);var polished=tint(basalt,79,68,86);var bricks=copy(polished);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(y%8==0||Math.floorMod(x+(y/8)*8,16)==0)bricks.setRGB(x,y,0xff332c3b);
        GenerateProjectTextures.seams(bricks,true);
        GenerateProjectTextures.save(vault,"block/stone","vaultstone");GenerateProjectTextures.save(weathered,"block/stone","weathered_vaultstone");
        for(int variant=1;variant<=2;variant++){
            GenerateProjectTextures.save(variation(vault,variant),"block/stone","vaultstone_"+variant);
            GenerateProjectTextures.save(variation(weathered,variant),"block/stone","weathered_vaultstone_"+variant);
        }
        GenerateProjectTextures.save(polished,"block/stone","polished_vaultstone");GenerateProjectTextures.save(bricks,"block/stone","vaultstone_bricks");
        var original=ImageIO.read(GenerateProjectTextures.ROOT.resolve("block/riftstone.png").toFile());var stone=tint(basalt,46,41,60);preserveEdges(stone,original);
        GenerateProjectTextures.save(stone,"block","riftstone");
        for(String name:new String[]{"riftsilver_ore","rift_frame","abyssal_turf_side"}){
            var im=ImageIO.read(GenerateProjectTextures.ROOT.resolve("block/"+name+".png").toFile());var before=copy(im);
            for(int y=1;y<15;y++)for(int x=1;x<15;x++)if(im.getRGB(x,y)==original.getRGB(x,y))im.setRGB(x,y,stone.getRGB(x,y));
            preserveEdges(im,before);GenerateProjectTextures.save(im,"block",name);
        }
        String[] reaction={"vitriolite","pyrolith","aerolite","phosphorite"};int[][] colors={{44,43,55},{77,37,47},{95,106,111},{128,132,91}};
        for(int i=0;i<reaction.length;i++)GenerateProjectTextures.save(tint(i==1?schist:basalt,colors[i][0],colors[i][1],colors[i][2]),"block",reaction[i]);
        Path preview=Path.of("art/generated/project-textures/geology-sheet.png");BufferedImage sheet=new BufferedImage(640,160,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();
        g.setColor(new Color(0x17141c));g.fillRect(0,0,640,160);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        BufferedImage[] tiles={stone,vault,weathered,polished,bricks};for(int i=0;i<tiles.length;i++)g.drawImage(tiles[i],i*128+8,16,112,112,null);g.dispose();ImageIO.write(sheet,"PNG",preview.toFile());
    }
}
