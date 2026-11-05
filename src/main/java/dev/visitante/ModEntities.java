package dev.visitante;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final EntityType<VisitorEntity> VISITOR = Registry.register(
        Registries.ENTITY_TYPE,
        new Identifier("visitante", "visitor"),
        EntityType.Builder.create(VisitorEntity::new, SpawnGroup.MONSTER)
            .setDimensions(0.6f, 2.4f)
            .build("visitor"));

    public static void register() {
        FabricDefaultAttributeRegistry.register(VISITOR, VisitorEntity.createVisitorAttributes());
    }
}
