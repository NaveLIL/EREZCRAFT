package pro.erez.interstice.worldgen;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

/** Reloadable vanilla trunk/foliage recipes. Parsing commits a complete valid catalog atomically. */
public final class GardenTreeDefinitions extends SimpleJsonResourceReloadListener {
    public record Variant(int weight, TreeConfiguration tree, String branchPath, int stemWidth, int extraHeight,int vineAttempts,int vineLength,String joCode) {
        public static final Codec<Variant> CODEC = RecordCodecBuilder.<Variant>create(i -> i.group(
                Codec.intRange(1, 32).fieldOf("weight").forGetter(Variant::weight),
                TreeConfiguration.CODEC.fieldOf("tree").forGetter(Variant::tree),
                Codec.STRING.optionalFieldOf("branch_path", "").forGetter(Variant::branchPath),
                Codec.intRange(1,2).optionalFieldOf("stem_width",1).forGetter(Variant::stemWidth),
                Codec.intRange(0,3).optionalFieldOf("extra_height",0).forGetter(Variant::extraHeight),
                Codec.intRange(0,8).optionalFieldOf("vine_attempts",5).forGetter(Variant::vineAttempts),
                Codec.intRange(1,8).optionalFieldOf("vine_length",6).forGetter(Variant::vineLength),
                Codec.STRING.optionalFieldOf("jo_code", "").forGetter(Variant::joCode)).apply(i, Variant::new))
                .validate(v -> {
                    try {
                        if(!v.branchPath.isEmpty()&&!v.joCode.isEmpty())throw new IllegalArgumentException("Choose branch_path or jo_code, not both");
                        if(!v.branchPath.isEmpty()||!v.joCode.isEmpty())JoShape.draw(v.code(),net.minecraft.core.BlockPos.ZERO,0,v.extraHeight);
                    }
                    catch(IllegalArgumentException error){return com.mojang.serialization.DataResult.error(error::getMessage);}
                    return com.mojang.serialization.DataResult.success(v);
                });
        public String code(){return joCode.isEmpty()?JoShape.encodePath(branchPath):joCode;}
    }
    public record Definition(int attempts, int chance, List<Variant> variants) {
        public static final Codec<Definition> CODEC = RecordCodecBuilder.<Definition>create(i -> i.group(
                Codec.intRange(0, 4).fieldOf("attempts").forGetter(Definition::attempts),
                Codec.intRange(1, 64).fieldOf("chance").forGetter(Definition::chance),
                Variant.CODEC.listOf().fieldOf("variants").forGetter(Definition::variants)).apply(i, Definition::new))
                .validate(d -> d.variants.isEmpty() || d.variants.size() > 8
                        || d.variants.stream().anyMatch(v -> v.tree.rootPlacer.isPresent() || !v.tree.decorators.isEmpty())
                        ? com.mojang.serialization.DataResult.error(() -> "Use 1..8 trunk/foliage variants; root placers/decorators are not supported by the checked tree planner")
                        : com.mojang.serialization.DataResult.success(d));
        public Variant select(RandomSource random) {
            int choice = random.nextInt(variants.stream().mapToInt(Variant::weight).sum());
            for (Variant variant : variants) { choice -= variant.weight; if (choice < 0) return variant; }
            throw new AssertionError("Invalid weighted tree catalog");
        }
    }
    public static final ResourceLocation PALEHEART = ResourceLocation.fromNamespaceAndPath("interstice", "paleheart");
    private static volatile Map<ResourceLocation, Definition> catalog = Map.of();
    public GardenTreeDefinitions() { super(new Gson(), "interstice_trees"); }
    public static Definition get(ResourceLocation id) {
        Definition definition = catalog.get(id);
        if (definition == null) throw new IllegalStateException("Missing tree definition " + id);
        return definition;
    }
    @Override protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Definition> parsed = new java.util.HashMap<>();
        entries.forEach((id, data) -> parsed.put(id, Definition.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow()));
        if (!parsed.containsKey(PALEHEART)) throw new IllegalStateException("Missing required paleheart tree catalog");
        catalog = Map.copyOf(parsed);
    }
}
