package dev.visitante;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class VisitorRenderer extends BipedEntityRenderer<VisitorEntity, BipedEntityModel<VisitorEntity>> {
    private static final Identifier TEXTURE = new Identifier("visitante", "textures/entity/visitor.png");

    public VisitorRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 0.3f);
    }

    @Override
    public Identifier getTexture(VisitorEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(VisitorEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(0.95f, 1.3f, 0.95f);
    }
}
