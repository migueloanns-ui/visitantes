package dev.visitante;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

public class VisitanteMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ModEntities.register();
        Director.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
            CommandManager.literal("visitante").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("nivel")
                    .then(CommandManager.argument("n", IntegerArgumentType.integer(0, 5)).executes(ctx -> {
                        Director.forcedStage = IntegerArgumentType.getInteger(ctx, "n");
                        ctx.getSource().sendFeedback(() -> Text.literal("Nivel: " + Director.forcedStage), false);
                        return 1;
                    })))
                .then(CommandManager.literal("agora").executes(ctx -> {
                    Director.forceNow(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("final").executes(ctx -> {
                    Meta.finale(ctx.getSource().getPlayerOrThrow().getServerWorld(), ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("limpar").executes(ctx -> {
                    Infection.reset();
                    return 1;
                }))
                .then(CommandManager.literal("aparecer").executes(ctx -> {
                    Director.forceSpawn(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
        ));
    }
}
