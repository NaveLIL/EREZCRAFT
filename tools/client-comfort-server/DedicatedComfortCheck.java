package pro.erezcraft.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

/** Functional compatibility gate in an isolated real dedicated server, not GameTestServer. */
@Mod("erezcraft_comfort_server_probe")
@EventBusSubscriber(modid="erezcraft_comfort_server_probe")
public final class DedicatedComfortCheck {
    public DedicatedComfortCheck() {}

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("erezcraft.comfortDedicatedProbe")) return;
        var server=event.getServer(); var result=new JsonObject();
        try {
            var directory=server.getServerDirectory().toAbsolutePath().normalize();
            if (!directory.toString().replace('\\','/').matches(".*/\\.verification/client-comfort-dedicated-20261010(?:-[a-z0-9]+)?/profile"))
                throw new IllegalStateException("Own isolated profile required: "+directory);
            if (!server.isDedicatedServer() || server.getClass().getName().contains("GameTest"))
                throw new IllegalStateException("Real dedicated server required: "+server.getClass().getName());
            if (Runtime.version().feature()!=21) throw new IllegalStateException("Java21 required");
            var loaded=new JsonObject();
            for (String mod:new String[]{"minecraft","neoforge","interstice","create","aeronautics","sable","distanthorizons","ae2","ars_nouveau","modern_industrialization","mekanism","mekanismgenerators","createaddition","storagedrawers","jade","jei","yigd","chunky","draconicevolution","brandonscore","codechickenlib","draconicadditions","ferritecore","modernfix","buildinggadgets2","farmersdelight"}) {
                var container=ModList.get().getModContainerById(mod).orElseThrow(()->new IllegalStateException("Missing critical server mod "+mod));
                loaded.addProperty(mod,container.getModInfo().getVersion().toString());
            }
            if (!loaded.get("minecraft").getAsString().equals("1.21.1") || !loaded.get("neoforge").getAsString().equals("21.1.252"))
                throw new IllegalStateException("Runtime versions differ: "+loaded);
            for (String clientOnly:new String[]{"controlling","searchables","inventoryprofilesnext","libipn","kotlinforforge","iris","sodium","lambdynlights","immediatelyfast","derenderpatcher","mousetweaks","xaerominimap","xaeroworldmap"})
                if (ModList.get().isLoaded(clientOnly)) throw new IllegalStateException("Client-only component loaded on server: "+clientOnly);
            var v6=server.getLevel(IslandWorld.TENSION_WORLD);
            if (v6==null || !v6.dimension().location().toString().equals("interstice:islands_v6") || !(v6.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator) || generator.terrainRevision()!=6)
                throw new IllegalStateException("Actual V6 key and generator revision6 are required");
            int fusion=0, cooking=0, gadgets=0;
            for (var holder:server.getRecipeManager().getRecipes()) {
                var type=String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()));
                if ("draconicevolution:fusion_crafting".equals(type)) fusion++;
                if ("farmersdelight:cooking".equals(type)) cooking++;
                if ("buildinggadgets2".equals(holder.id().getNamespace())) gadgets++;
            }
            if (fusion<35 || cooking<20 || gadgets<6) throw new IllegalStateException("Missing recipe registrations: "+fusion+","+cooking+","+gadgets);
            var gadget=BuiltInRegistries.ITEM.get(id("buildinggadgets2:gadget_building"));
            var potBlock=BuiltInRegistries.BLOCK.get(id("farmersdelight:cooking_pot"));
            if (gadget==Items.AIR || potBlock==Blocks.AIR) throw new IllegalStateException("Missing new registered content");
            var buildingRecipe=server.getRecipeManager().byKey(id("buildinggadgets2:gadget_building")).orElseThrow();
            if (!(buildingRecipe.value() instanceof CraftingRecipe recipe)) throw new IllegalStateException("Wrong building gadget recipe type");
            var input=CraftingInput.of(3,3,List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.REDSTONE),new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.DIAMOND),new ItemStack(Items.REDSTONE),new ItemStack(Items.DIAMOND),
                new ItemStack(Items.IRON_INGOT),new ItemStack(Items.LAPIS_LAZULI),new ItemStack(Items.IRON_INGOT)));
            if (!recipe.matches(input,server.overworld()) || recipe.assemble(input,server.registryAccess()).getItem()!=gadget)
                throw new IllegalStateException("Original Building Gadget crafting recipe failed with tagged ingredients");
            // Exercise the original cooking block entity and recipe manager on actual server world blocks.
            // The bounded native processing calls avoid inventing a benchmark from this functional fixture.
            var world=server.overworld(); BlockPos pos=world.getSharedSpawnPos().above(4);
            world.setBlock(pos.below(),Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,true),3);
            world.setBlock(pos,potBlock.defaultBlockState(),3);
            if (!(world.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) throw new IllegalStateException("Cooking Pot block entity missing");
            var inventory=pot.getInventory();
            inventory.setStackInSlot(0,new ItemStack(Items.CARROT)); inventory.setStackInSlot(1,new ItemStack(Items.POTATO));
            inventory.setStackInSlot(2,new ItemStack(Items.BEETROOT));
            inventory.setStackInSlot(3,new ItemStack(BuiltInRegistries.ITEM.get(id("farmersdelight:cabbage"))));
            inventory.setStackInSlot(CookingPotBlockEntity.CONTAINER_SLOT,new ItemStack(Items.BOWL));
            boolean heated=pot.isHeated();
            for (int tick=0;tick<300;tick++) CookingPotBlockEntity.cookingTick(world,pos,world.getBlockState(pos),pot);
            var soup=inventory.getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT);
            boolean consumed=true;for (int slot=0;slot<4;slot++) consumed&=inventory.getStackInSlot(slot).isEmpty();
            if (!heated || !consumed || soup.getCount()!=1 || !BuiltInRegistries.ITEM.getKey(soup.getItem()).equals(id("farmersdelight:vegetable_soup")))
                throw new IllegalStateException("Native soup cooking failed: heated="+heated+",consumed="+consumed+",output="+soup);
            result.add("critical_mod_versions",loaded); result.addProperty("server_class",server.getClass().getName());
            result.addProperty("actual_v6_dimension",v6.dimension().location().toString()); result.addProperty("actual_v6_generator_revision",generator.terrainRevision());
            result.addProperty("actual_seed",v6.getSeed()); result.addProperty("fusion_recipe_count",fusion);
            result.addProperty("farmersdelight_cooking_recipe_count",cooking); result.addProperty("buildinggadgets_recipe_count",gadgets);
            result.addProperty("building_gadget_original_craft_matches",true); result.addProperty("building_gadget_original_craft_output","buildinggadgets2:gadget_building");
            result.addProperty("cooking_world",world.dimension().location().toString()); result.addProperty("cooking_native_processing_calls",300);
            result.addProperty("cooking_pot_heated",heated); result.addProperty("cooking_ingredients_consumed",consumed);
            result.addProperty("cooking_output",BuiltInRegistries.ITEM.getKey(soup.getItem()).toString()); result.addProperty("cooking_output_count",soup.getCount());
            result.addProperty("java",System.getProperty("java.version")); result.addProperty("passed",true);
        } catch(Throwable failure) {
            result.addProperty("passed",false);result.addProperty("error",failure.toString());failure.printStackTrace();
        }
        result.addProperty("server_pid",ProcessHandle.current().pid());
        result.addProperty("scope","Real isolated dedicated startup, critical mod versions, original recipe registrations, original Building Gadget crafting match/assembly, native Cooking Pot processing on placed blocks, actual V6 dimension/revision. No 10-player capacity, target hardware performance, Survival progression or multiplayer Building Gadget claim.");
        try { Files.writeString(server.getServerDirectory().resolve("client-comfort-server-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch(Exception failure) { throw new IllegalStateException(failure); }
        System.out.println("EREZCRAFT_COMFORT_DEDICATED_PROBE "+result);
        server.halt(false);
    }
}
