package pro.erez.interstice.client;

import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Cosmetic fixed-step cloth cages. Waist pinned to torso; both posed legs are collision constraints. */
public final class EngineerCloth {
    public static final int ROWS=12,COLS=12,SIDE_NODES=ROWS*COLS,NODES=SIDE_NODES*2;
    public static final float TOP=8.1F,BOTTOM=20.1F;
    private record Link(int a,int b,float length,float stiffness){}
    public record Box(Matrix4f toBody,Matrix4f toLocal,float halfX,float minY,float maxY,float front,float back){
        boolean interval(Vector3f origin,Vector3f direction,float[] range,Vector3f p,Vector3f d){
            toLocal.transformPosition(origin,p);toLocal.transformDirection(direction,d);float near=-1e9F,far=1e9F;
            for(int axis=0;axis<3;axis++){
                float o=p.get(axis),v=d.get(axis),low=axis==0?-halfX:axis==1?minY:-front,high=axis==0?halfX:axis==1?maxY:back;
                if(Math.abs(v)<1e-7F){if(o<low||o>high)return false;continue;}
                float a=(low-o)/v,b=(high-o)/v;if(a>b){float swap=a;a=b;b=swap;}near=Math.max(near,a);far=Math.min(far,b);if(near>far)return false;
            }range[0]=near;range[1]=far;return far>0;
        }
        public float penetration(Vector3f bodyPoint){
            var p=toLocal.transformPosition(bodyPoint,new Vector3f());
            if(p.y<minY||p.y>maxY||p.x<=-halfX||p.x>=halfX||p.z<=-front||p.z>=back)return 0;
            return Math.min(Math.min(p.x+halfX,halfX-p.x),Math.min(p.z+front,back-p.z));
        }
        public boolean project(Vector3f point,Vector3f scratch){
            toLocal.transformPosition(point,scratch);
            if(scratch.y<minY||scratch.y>maxY||scratch.x<=-halfX||scratch.x>=halfX||scratch.z<=-front||scratch.z>=back)return false;
            float x0=scratch.x+halfX,x1=halfX-scratch.x,z0=scratch.z+front,z1=back-scratch.z;
            float min=Math.min(Math.min(x0,x1),Math.min(z0,z1));
            if(min==x0)scratch.x=-halfX-.002F;else if(min==x1)scratch.x=halfX+.002F;
            else if(min==z0)scratch.z=-front-.002F;else scratch.z=back+.002F;
            toBody.transformPosition(scratch,point);return true;
        }
    }
    public static final class State{
        private final Vector3f[] rest=new Vector3f[NODES],positions=new Vector3f[NODES],previous=new Vector3f[NODES],stepBefore=new Vector3f[NODES];
        private final List<Link> links=new ArrayList<>();
        private final Vector3f scratch=new Vector3f(),target=new Vector3f(),oldMotion=new Vector3f(),gravity=new Vector3f(0,.035F,0),direction=new Vector3f(),rayPoint=new Vector3f(),rayDirection=new Vector3f();
        private final float[] near=new float[8],far=new float[8],range=new float[2];
        private final Matrix4f[] legs={new Matrix4f(),new Matrix4f()};
        private Box[] boxes=new Box[0];private int lastTick=Integer.MIN_VALUE;private boolean initialized;
        public int steps;public float maximumDisplacement;
        public State(){
            for(int side=0;side<2;side++)for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){
                int i=index(side,r,c);rest[i]=restPoint(side,r/(float)(ROWS-1),c);positions[i]=new Vector3f(rest[i]);previous[i]=new Vector3f(rest[i]);stepBefore[i]=new Vector3f(rest[i]);
            }
            for(int s=0;s<2;s++)for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++){
                if(r+1<ROWS)link(index(s,r,c),index(s,r+1,c),1);
                if(c+1<COLS)link(index(s,r,c),index(s,r,c+1),1);
                if(r+1<ROWS&&c+1<COLS){link(index(s,r,c),index(s,r+1,c+1),.55F);link(index(s,r,c+1),index(s,r+1,c),.55F);}
                if(r+2<ROWS)link(index(s,r,c),index(s,r+2,c),.12F);
            }
        }
        private void link(int a,int b,float stiffness){links.add(new Link(a,b,rest[a].distance(rest[b]),stiffness));}
        public Box[] boxes(){return boxes;}
        public Vector3f node(int side,int row,int column){return new Vector3f(positions[index(side,row,column)]);}
        public void reset(){initialized=false;lastTick=Integer.MIN_VALUE;steps=0;}
        public void pose(Matrix4f bodyToModel,Matrix4f rightLegToModel,Matrix4f leftLegToModel){
            var inverse=new Matrix4f(bodyToModel).invert();inverse.mul(rightLegToModel,legs[0]);inverse.mul(leftLegToModel,legs[1]);
            inverse.transformDirection(0,.035F,0,gravity);
            var list=new ArrayList<Box>(8);
            for(var leg:legs){
                var inv=new Matrix4f(leg).invert();
                // Distinct knee/boot bounds include the visible trouser webbing and toe/sole geometry.
                list.add(new Box(leg,inv,2.32F,-.05F,4.44F,2.36F,2.36F));
                list.add(new Box(leg,inv,2.40F,4.44F,7.32F,2.78F,2.48F));
                list.add(new Box(leg,inv,2.32F,7.32F,8.58F,2.36F,2.36F));
                list.add(new Box(leg,inv,2.58F,8.58F,12.55F,3.16F,2.86F));
            }boxes=list.toArray(Box[]::new);
        }
        private void target(int i,Vector3f out){
            int side=i/SIDE_NODES,row=(i%SIDE_NODES)/COLS;float v=row/(float)(ROWS-1),follow=.48F*v*v;
            out.set(rest[i]);float px=side==0?-1.9F:1.9F;
            scratch.set(rest[i]).sub(px,12,0);legs[side].transformPosition(scratch);
            out.lerp(scratch,follow);
        }
        public void update(int tick,Vector3f motion){
            if(!initialized||tick<lastTick||tick-lastTick>5){
                for(int i=0;i<NODES;i++){target(i,target);positions[i].set(target);previous[i].set(target);stepBefore[i].set(target);}
                for(int k=0;k<8;k++)solve();
                for(int i=0;i<NODES;i++){previous[i].set(positions[i]);stepBefore[i].set(positions[i]);}
                initialized=true;lastTick=tick;oldMotion.set(motion);return;
            }
            if(tick==lastTick)return;
            int elapsed=Math.min(4,tick-lastTick);lastTick=tick;
            var acceleration=new Vector3f(motion).sub(oldMotion);oldMotion.set(motion);
            if(acceleration.length()>2)acceleration.normalize(2);
            for(int step=0;step<elapsed;step++){
                for(int i=0;i<NODES;i++){
                    stepBefore[i].set(positions[i]);if(pinned(i))continue;
                    float ox=positions[i].x,oy=positions[i].y,oz=positions[i].z;
                    positions[i].add((ox-previous[i].x)*.82F+gravity.x-acceleration.x*.12F,
                            (oy-previous[i].y)*.82F+gravity.y-acceleration.y*.10F,(oz-previous[i].z)*.82F+gravity.z-acceleration.z*.12F);
                    previous[i].set(ox,oy,oz);
                }
                for(int k=0;k<8;k++)solve();steps++;
            }
        }
        private void solve(){
            for(var link:links){
                Vector3f a=positions[link.a],b=positions[link.b];float dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;
                float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(length<1e-5)continue;
                float wa=pinned(link.a)?0:1,wb=pinned(link.b)?0:1;if(wa+wb==0)continue;
                float f=(length-link.length)/length*link.stiffness/(wa+wb);
                if(wa>0)a.add(dx*f,dy*f,dz*f);if(wb>0)b.sub(dx*f,dy*f,dz*f);
            }
            for(int i=0;i<NODES;i++){
                if(pinned(i)){positions[i].set(rest[i]);continue;}
                target(i,target);positions[i].lerp(target,.035F);
                contact(positions[i],i/SIDE_NODES,i%COLS);
                if(!positions[i].isFinite())throw new IllegalStateException("Non-finite engineer cloth");
                maximumDisplacement=Math.max(maximumDisplacement,positions[i].distance(rest[i]));
            }
        }
        /** Blend cage displacement, then perform final posed-leg contact on each rendered vertex. */
        public void deform(String name,int side,float gx,float gy,float gz,float partial,Vector3f out){
            float v=clamp((gy-TOP)/(BOTTOM-TOP)),outer=lerp(4.35F,6.15F,v),u;
            if(name.contains("side_gusset"))u=4+3*clamp((gz-lerp(-2.65F,-3.05F,v))/(lerp(2.95F,3.35F,v)-lerp(-2.65F,-3.05F,v)));
            else if(name.contains("rear_")||name.contains("back_fold"))u=7+4*clamp((outer-Math.abs(gx))/(outer-lerp(.45F,.85F,v)));
            else u=4*clamp((Math.abs(gx)-lerp(.45F,2.1F,v))/(outer-lerp(.45F,2.1F,v)));
            float rr=v*(ROWS-1);int r=Math.min(ROWS-2,(int)rr),c=Math.min(COLS-2,(int)u);float rv=rr-r,cu=u-c;
            out.set(gx,gy,gz);float alpha=clamp(partial);
            for(int dr=0;dr<=1;dr++)for(int dc=0;dc<=1;dc++){
                int i=index(side,r+dr,c+dc);float weight=(dr==0?1-rv:rv)*(dc==0?1-cu:cu);
                out.add((lerp(stepBefore[i].x,positions[i].x,alpha)-rest[i].x)*weight,
                        (lerp(stepBefore[i].y,positions[i].y,alpha)-rest[i].y)*weight,(lerp(stepBefore[i].z,positions[i].z,alpha)-rest[i].z)*weight);
            }
            if(gy>TOP+.02F)contact(out,side,u);
        }
        /** Exit the union of posed leg volumes along the cloth's continuous outward direction.
         * A union ray avoids oscillation between overlapping left/right thigh boxes in crouch. */
        private void contact(Vector3f point,int side,float column){
            float angle=column<=3?-(float)Math.PI/2:column<5.5F?-(float)Math.PI/2+(column-3)/2.5F*(float)Math.PI/2:
                    column<=6.5F?0:column<8.5F?(column-6.5F)/2*(float)Math.PI/2:(float)Math.PI/2;
            direction.set((side==0?-1:1)*(float)Math.cos(angle),0,(float)Math.sin(angle));boolean inside=false;
            for(int i=0;i<boxes.length;i++){
                if(boxes[i].interval(point,direction,range,rayPoint,rayDirection)){near[i]=range[0];far[i]=range[1];if(near[i]<0&&far[i]>0)inside=true;}
                else{near[i]=Float.POSITIVE_INFINITY;far[i]=Float.NEGATIVE_INFINITY;}
            }
            if(!inside)return;float exit=0;
            for(int pass=0;pass<boxes.length+1;pass++){
                float previous=exit;for(int i=0;i<boxes.length;i++)if(near[i]<=exit+.0001F&&far[i]>=exit)exit=Math.max(exit,far[i]+.003F);
                if(exit==previous)break;
            }
            if(!Float.isFinite(exit)||exit>40)throw new IllegalStateException("Unbounded cloth contact correction");
            point.fma(exit,direction);
        }
        public float penetration(Vector3f point){float p=0;for(var box:boxes)p=Math.max(p,box.penetration(point));return p;}
        public float pinnedError(){float e=0;for(int i=0;i<NODES;i++)if(pinned(i))e=Math.max(e,positions[i].distance(rest[i]));return e;}
    }
    private static int index(int side,int row,int column){return side*SIDE_NODES+row*COLS+column;}
    private static boolean pinned(int index){return index%SIDE_NODES<COLS;}
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static Vector3f restPoint(int side,float v,float column){
        float sign=side==0?-1:1,innerFront=lerp(.45F,2.1F,v),outer=lerp(4.35F,6.15F,v),innerBack=lerp(.45F,.85F,v);
        float front=lerp(-2.65F,-3.05F,v),back=lerp(2.95F,3.35F,v),x,z;
        if(column<=4){x=lerp(innerFront,outer,column/4);z=front;}
        else if(column<=7){x=outer;z=lerp(front,back,(column-4)/3);}
        else{x=lerp(outer,innerBack,(column-7)/4);z=back;}
        return new Vector3f(sign*x,lerp(TOP,BOTTOM,v),z);
    }
}
