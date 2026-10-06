package pro.erez.interstice.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Vector3f;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.LightFluid;

@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class FluidClient {
    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new Appearance(true), Interstice.LIGHT_TYPE.get());
        event.registerFluidType(new Appearance(false), Interstice.HEAVY_TYPE.get());
    }
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(Interstice.LIGHT.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Interstice.LIGHT_FLOW.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Interstice.HEAVY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Interstice.HEAVY_FLOW.get(), RenderType.translucent());
        });
    }
    private static final class Appearance implements IClientFluidTypeExtensions {
        private final boolean light;
        Appearance(boolean light) { this.light = light; }
        @Override public ResourceLocation getStillTexture() { return ResourceLocation.fromNamespaceAndPath(Interstice.ID, light ? "block/light_still_v2" : "block/heavy_still"); }
        @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.fromNamespaceAndPath(Interstice.ID, light ? "block/light_flow_v2" : "block/heavy_flow"); }
        @Override public int getTintColor() { return light ? 0xD8FFFFFF : 0xFFFFFFFF; }
        @Override public Vector3f modifyFogColor(Camera camera, float partial, ClientLevel world, int distance, float darkness, Vector3f color) {
            return light ? new Vector3f(0.70F, 0.69F, 0.47F) : new Vector3f(0.23F, 0.02F, 0.055F);
        }
        @Override public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float distance, float partial, float near, float far, FogShape shape) {
            RenderSystem.setShaderFogStart(0);
            RenderSystem.setShaderFogEnd(light ? 5 : 3);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        }
        @Override public boolean renderFluid(FluidState state, BlockAndTintGetter world, BlockPos pos, VertexConsumer consumer, BlockState block) {
            if (!light) return false;
            renderCeilingFluid(state, world, pos, consumer, this);
            return true;
        }
    }
    /** Flat voxel surfaces for the prototype, filled from the ceiling down, including the visible underside. */
    private static void renderCeilingFluid(FluidState state, BlockAndTintGetter world, BlockPos pos, VertexConsumer out, Appearance appearance) {
        float x = pos.getX() & 15, y = pos.getY() & 15, z = pos.getZ() & 15;
        float bottom = 1 - state.getHeight(world, pos);
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite still = atlas.apply(appearance.getStillTexture());
        TextureAtlasSprite flow = atlas.apply(appearance.getFlowingTexture());
        int light = LevelRenderer.getLightColor(world, pos);
        int color = appearance.getTintColor();
        if (visible(world, pos.above())) {
            quad(out, still, color, light, 0, 1, 0,
                    x,y+1,z, x,y+1,z+1, x+1,y+1,z+1, x+1,y+1,z);
        }
        if (visible(world, pos.below()) || bottom > 0) {
            quad(out, still, color, light, 0, -1, 0,
                    x,y+bottom,z, x+1,y+bottom,z, x+1,y+bottom,z+1, x,y+bottom,z+1);
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(side);
            FluidState other = world.getFluidState(neighbor);
            float top = 1;
            if (other.getType() instanceof LightFluid) top = 1 - other.getHeight(world, neighbor);
            else if (world.getBlockState(neighbor).isSolidRender(world, neighbor)) continue;
            if (top <= bottom) continue;
            switch (side) {
                case NORTH -> quad(out, flow, color, light, 0,0,-1, x,y+bottom,z, x,y+top,z, x+1,y+top,z, x+1,y+bottom,z);
                case SOUTH -> quad(out, flow, color, light, 0,0,1, x+1,y+bottom,z+1, x+1,y+top,z+1, x,y+top,z+1, x,y+bottom,z+1);
                case WEST -> quad(out, flow, color, light, -1,0,0, x,y+bottom,z+1, x,y+top,z+1, x,y+top,z, x,y+bottom,z);
                case EAST -> quad(out, flow, color, light, 1,0,0, x+1,y+bottom,z, x+1,y+top,z, x+1,y+top,z+1, x+1,y+bottom,z+1);
                default -> {}
            }
        }
    }
    private static boolean visible(BlockAndTintGetter world, BlockPos pos) {
        return !(world.getFluidState(pos).getType() instanceof LightFluid) && !world.getBlockState(pos).isSolidRender(world, pos);
    }
    private static void quad(VertexConsumer out, TextureAtlasSprite sprite, int color, int light, float nx, float ny, float nz, float... xyz) {
        // Flow sprites are 32x32; half a tile keeps Minecraft's usual 16 texels per block.
        float span = ny == 0 ? 0.5F : 1.0F;
        float height = Math.abs(xyz[4] - xyz[1]);
        float endV = sprite.getV(ny == 0 ? span * height : span);
        float[] u = {sprite.getU0(), sprite.getU0(), sprite.getU(span), sprite.getU(span)};
        float[] v = {endV, sprite.getV0(), sprite.getV0(), endV};
        // Both sides are visible: players can view the ocean from outside and from inside.
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                int p = side == 0 ? i : 3 - i;
                out.addVertex(xyz[p*3], xyz[p*3+1], xyz[p*3+2])
                        .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
                        .setUv(u[p], v[p]).setLight(light).setNormal(side == 0 ? nx : -nx, side == 0 ? ny : -ny, side == 0 ? nz : -nz);
            }
        }
    }
}
