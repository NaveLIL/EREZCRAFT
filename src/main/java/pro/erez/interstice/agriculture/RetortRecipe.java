package pro.erez.interstice.agriculture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Counted unordered ingredients. Integral max-flow handles overlapping tags without greedy allocation. */
public record RetortRecipe(List<Input> inputs,List<ItemStack> outputs,int ticks) implements Recipe<RetortInput> {
    public record Input(Ingredient ingredient,int count){
        public static final Codec<Input> CODEC=RecordCodecBuilder.create(i->i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Input::ingredient),
                Codec.intRange(1,64).fieldOf("count").forGetter(Input::count)).apply(i,Input::new));
    }
    public static final MapCodec<RetortRecipe> CODEC=RecordCodecBuilder.<RetortRecipe>mapCodec(i->i.group(
            Input.CODEC.listOf().fieldOf("inputs").forGetter(RetortRecipe::inputs),
            ItemStack.CODEC.listOf().fieldOf("outputs").forGetter(RetortRecipe::outputs),
            Codec.intRange(20,32000).fieldOf("ticks").forGetter(RetortRecipe::ticks)).apply(i,RetortRecipe::new))
            .validate(r->r.inputs.isEmpty()||r.inputs.size()>6||r.outputs.isEmpty()||r.outputs.size()>3
                    ||r.outputs.stream().anyMatch(s->s.isEmpty()||s.getCount()>s.getMaxStackSize())
                    ?com.mojang.serialization.DataResult.error(()->"Use1..6 counted inputs and1..3 valid output stacks")
                    :com.mojang.serialization.DataResult.success(r));
    public RetortRecipe { inputs=List.copyOf(inputs);outputs=outputs.stream().map(ItemStack::copy).toList(); }
    public int[] allocation(RetortInput inventory){
        int n=inputs.size(),sink=n+7,nodes=sink+1;int[][] capacity=new int[nodes][nodes];int total=0;
        for(int i=0;i<n;i++){
            capacity[0][i+1]=inputs.get(i).count;total+=inputs.get(i).count;
            for(int s=0;s<6;s++)if(inputs.get(i).ingredient.test(inventory.getItem(s)))capacity[i+1][n+1+s]=64;
        }
        for(int s=0;s<6;s++)capacity[n+1+s][sink]=inventory.getItem(s).getCount();
        int flowed=0;
        while(flowed<total){
            int[] parent=new int[nodes];Arrays.fill(parent,-1);parent[0]=0;var queue=new ArrayDeque<Integer>();queue.add(0);
            while(!queue.isEmpty()&&parent[sink]<0){int at=queue.remove();for(int next=1;next<nodes;next++)if(parent[next]<0&&capacity[at][next]>0){parent[next]=at;queue.add(next);}}
            if(parent[sink]<0)return null;
            int take=total-flowed;for(int at=sink;at!=0;at=parent[at])take=Math.min(take,capacity[parent[at]][at]);
            for(int at=sink;at!=0;at=parent[at]){capacity[parent[at]][at]-=take;capacity[at][parent[at]]+=take;}flowed+=take;
        }
        int[] result=new int[6];for(int s=0;s<6;s++)result[s]=capacity[sink][n+1+s];return result;
    }
    @Override public boolean matches(RetortInput input,Level level){return allocation(input)!=null;}
    @Override public ItemStack assemble(RetortInput input,HolderLookup.Provider lookup){return outputs.getFirst().copy();}
    @Override public ItemStack getResultItem(HolderLookup.Provider lookup){return outputs.getFirst().copy();}
    @Override public boolean canCraftInDimensions(int w,int h){return w*h>=inputs.size();}
    @Override public RecipeSerializer<?> getSerializer(){return RealmAgriculture.RETORT_SERIALIZER.get();}
    @Override public RecipeType<?> getType(){return RealmAgriculture.RETORT_RECIPE_TYPE.get();}
    @Override public boolean isSpecial(){return true;}
    @Override public NonNullList<Ingredient> getIngredients(){var result=NonNullList.<Ingredient>create();inputs.forEach(i->result.add(i.ingredient));return result;}
    public static final class Serializer implements RecipeSerializer<RetortRecipe>{
        private static final StreamCodec<RegistryFriendlyByteBuf,RetortRecipe> STREAM=StreamCodec.of(
                (buffer,recipe)->buffer.writeUtf(CODEC.codec().encodeStart(RegistryOps.create(JsonOps.INSTANCE,buffer.registryAccess()),recipe).getOrThrow().toString(),16384),
                buffer->CODEC.codec().parse(RegistryOps.create(JsonOps.INSTANCE,buffer.registryAccess()),com.google.gson.JsonParser.parseString(buffer.readUtf(16384))).getOrThrow());
        @Override public MapCodec<RetortRecipe> codec(){return CODEC;}
        @Override public StreamCodec<RegistryFriendlyByteBuf,RetortRecipe> streamCodec(){return STREAM;}
    }
}
