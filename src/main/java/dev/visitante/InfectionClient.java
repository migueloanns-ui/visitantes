package dev.visitante;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/** Tudo aqui e so visual, dentro da janela do jogo. */
@Environment(EnvType.CLIENT)
public class InfectionClient {
    private static final Random RNG = new Random();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static boolean crashShown = false;
    private static int tickCounter = 0;

    static String user() {
        String u = System.getProperty("user.name");
        return (u == null || u.isBlank()) ? "você" : u;
    }

    public static void init() {
        Infection.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (Infection.level < 1 || client.getWindow() == null) return;
            if (++tickCounter % 100 != 0) return;
            String title;
            int lvl = Infection.level;
            if (lvl == 1) title = "Minecraft — são " + LocalTime.now().format(FMT);
            else if (lvl == 2) title = "ele está aqui";
            else title = "não feche";
            client.getWindow().setTitle(title);
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof TitleScreen) || Infection.level < 1) return;
            rename(screen);
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> overlay(client, s, ctx));
            if (Infection.level >= 2 && !crashShown && RNG.nextInt(100) < 70) {
                crashShown = true;
                client.execute(() -> client.setScreen(new FakeCrashScreen()));
            }
        });
    }

    private static void rename(Screen screen) {
        int lvl = Infection.level;
        for (ClickableWidget b : Screens.getButtons(screen)) {
            if (b.getMessage().getContent() instanceof TranslatableTextContent t) {
                String key = t.getKey();
                if (key.equals("menu.singleplayer")) b.setMessage(Text.literal(lvl >= 2 ? "Voltar pra ele" : "Continuar"));
                else if (key.equals("menu.multiplayer")) b.setMessage(Text.literal("Você não está sozinho"));
                else if (key.equals("menu.online")) b.setMessage(Text.literal("Ele está aqui"));
                else if (key.equals("menu.options")) b.setMessage(Text.literal("..."));
                else if (key.equals("menu.quit")) b.setMessage(Text.literal("Sair (não adianta)"));
            }
        }
    }

    private static void overlay(MinecraftClient client, Screen s, DrawContext ctx) {
        long t = System.currentTimeMillis();
        int lvl = Infection.level;
        String hm = LocalTime.now().format(FMT);

        ctx.drawTextWithShadow(client.textRenderer, Text.literal("são " + hm + ", " + user() + "."),
            6, s.height - 12, 0xFFAA2222);

        if (lvl >= 2 && (t / 90) % 45 == 0) {
            ctx.getMatrices().push();
            ctx.getMatrices().scale(3.0f, 3.0f, 1.0f);
            ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("ELE ESTÁ AQUI"),
                s.width / 6, s.height / 6, 0xFF880000);
            ctx.getMatrices().pop();
        }

        if (lvl >= 3 && (t / 1000) % 19 == 0) {
            ctx.fill(0, 0, s.width, s.height, 0xFF000000);
            ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("ele está olhando"),
                s.width / 2, s.height / 2, 0xFFAA0000);
        }
    }
}
