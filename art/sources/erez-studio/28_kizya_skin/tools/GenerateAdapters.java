import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
/** Empty game-tag adapters for the offline editor exporter; no world/game simulation. */
public class GenerateAdapters {
 public static void main(String[] args)throws Exception {
  String[][] types={{"HeadlessBlockHandler","com.tom.cpl.block.BlockStateHandler","com.tom.cpl.block.BlockState"},{"HeadlessItemHandler","com.tom.cpl.item.ItemStackHandler","com.tom.cpl.item.Stack"},{"HeadlessEntityHandler","com.tom.cpl.block.entity.EntityTypeHandler","com.tom.cpl.block.entity.EntityType"},{"HeadlessBiomeHandler","com.tom.cpl.block.BiomeHandler","com.tom.cpl.block.Biome"}};
  for(String[] t:types){Class<?> cls=Class.forName(t[1],false,GenerateAdapters.class.getClassLoader());StringBuilder s=new StringBuilder("public class "+t[0]+" extends "+t[1]+"<Object> {\n");Set<String> seen=new HashSet<>();
   for(Method m:cls.getMethods())if(Modifier.isAbstract(m.getModifiers())){String key=m.getName()+Arrays.toString(m.getParameterTypes());if(!seen.add(key))continue;
    Class<?> r=m.getReturnType();String rt=m.getName().equals("emptyObject")?t[2]:r.getCanonicalName();s.append(" public "+rt+" "+m.getName()+"(");Class<?>[] p=m.getParameterTypes();for(int i=0;i<p.length;i++){if(i>0)s.append(',');s.append(p[i].getCanonicalName()+" p"+i);}s.append("){ ");
    String out=r==void.class?null:r==boolean.class?"false":r==char.class?"'\\0'":r.isPrimitive()?"0":java.util.List.class.isAssignableFrom(r)?"java.util.List.of()":r==String.class?"\"\"":"null";if(out!=null)s.append("return "+out+";");s.append(" }\n");
   }s.append("}\n");Files.writeString(Path.of(args[0],t[0]+".java"),s);
  }
 }
}
