package dev.visitante;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Tela falsa. Nada realmente travou: ESC volta ao menu. */
@Environment(EnvType.CLIENT)
public class FakeCrashScreen extends Screen {
    private final long opened = System.currentTimeMillis();

    public FakeCrashScreen() {
        super(Text.literal(""));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xFF000000);
        long el = System.currentTimeMillis() - opened;
        int cx = this.width / 2;
        int y = this.height / 2 - 30;
        if (el < 6000) {
            int pct = Math.min(99, (int) (el / 60));
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("O jogo parou de funcionar."), cx, y, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Erro: visitante.core (0xE000V)"), cx, y + 14, 0xFFAAAAAA);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Gerando relatório... " + pct + "%"), cx, y + 28, 0xFFAAAAAA);
        } else if (el < 9000) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("...mentira."), cx, y + 14, 0xFFAA0000);
        } else {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("eu ainda estou aqui, " + InfectionClient.user() + "."), cx, y + 14, 0xFFAA0000);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("(ESC)"), cx, y + 34, 0xFF444444);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
