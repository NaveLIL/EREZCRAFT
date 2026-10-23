package pro.erez.interstice.client;

import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import pro.erez.interstice.fauna.VaultHunter;

public final class VaultHunterRenderer extends MobRenderer<VaultHunter, VaultHunterModel> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("interstice", "textures/entity/vault_hunter.png");
    public static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath("interstice", "textures/entity/vault_hunter_eyes.png");
    public VaultHunterRenderer(EntityRendererProvider.Context context) {
        super(context, new VaultHunterModel(), .55F);
        addLayer(new net.minecraft.client.renderer.entity.layers.EyesLayer<VaultHunter,VaultHunterModel>(this) {
            @Override public net.minecraft.client.renderer.RenderType renderType() {
                return net.minecraft.client.renderer.RenderType.eyes(EYES);
            }
        });
    }
    @Override protected void scale(VaultHunter entity, PoseStack pose, float partialTick) {
        pose.scale(VaultHunterModel.SCALE,VaultHunterModel.SCALE,VaultHunterModel.SCALE);
    }
    @Override public ResourceLocation getTextureLocation(VaultHunter entity) { return TEXTURE; }
}
