import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/** Encode actual, evenly timed game captures as a small looping preview. */
public class GamePreviewGif {
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]),output=Path.of(args[1]);
        ImageWriter writer=ImageIO.getImageWritersByFormatName("gif").next();
        ImageWriteParam param=writer.getDefaultWriteParam();
        try(ImageOutputStream stream=ImageIO.createImageOutputStream(output.toFile())) {
            writer.setOutput(stream);writer.prepareWriteSequence(null);
            for(int frame=0;frame<8;frame++) {
                BufferedImage capture=ImageIO.read(source.resolve(String.format("lower-animation-frame-%02d.png",frame)).toFile());
                BufferedImage preview=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics=preview.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(capture,0,0,640,360,null);graphics.dispose();
                var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(preview),param);
                String format=metadata.getNativeMetadataFormatName();
                IIOMetadataNode tree=(IIOMetadataNode)metadata.getAsTree(format);
                IIOMetadataNode control=child(tree,"GraphicControlExtension");
                control.setAttribute("disposalMethod","none");control.setAttribute("userInputFlag","FALSE");
                control.setAttribute("transparentColorFlag","FALSE");control.setAttribute("delayTime","80");control.setAttribute("transparentColorIndex","0");
                if(frame==0) {
                    IIOMetadataNode extension=new IIOMetadataNode("ApplicationExtension");
                    extension.setAttribute("applicationID","NETSCAPE");extension.setAttribute("authenticationCode","2.0");
                    extension.setUserObject(new byte[]{1,0,0});child(tree,"ApplicationExtensions").appendChild(extension);
                }
                metadata.setFromTree(format,tree);writer.writeToSequence(new IIOImage(preview,null,metadata),param);
            }
            writer.endWriteSequence();
        } finally {writer.dispose();}
        BufferedImage a=ImageIO.read(source.resolve("03-lower-sea.png").toFile());
        BufferedImage b=ImageIO.read(source.resolve("03b-lower-sea-animation.png").toFile());
        long changed=0,count=0,difference=0;
        for(int y=a.getHeight()*45/100;y<a.getHeight()*85/100;y++)
            for(int x=a.getWidth()*20/100;x<a.getWidth()*80/100;x++) {
                int p=a.getRGB(x,y),q=b.getRGB(x,y);count++;if(p!=q) changed++;
                for(int shift:new int[]{0,8,16}) difference+=Math.abs(((p>>shift)&255)-((q>>shift)&255));
            }
        System.out.printf("Native ocean ROI changed %.2f%% of pixels; mean channel difference %.3f%n",100.0*changed/count,difference/(3.0*count));
        System.out.println(output);
    }
    static IIOMetadataNode child(IIOMetadataNode root,String name) {
        for(int i=0;i<root.getLength();i++) if(root.item(i).getNodeName().equals(name)) return (IIOMetadataNode)root.item(i);
        IIOMetadataNode node=new IIOMetadataNode(name);root.appendChild(node);return node;
    }
}
