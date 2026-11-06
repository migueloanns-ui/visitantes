package dev.visitante;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Meta {
    private static final Random RNG = new Random();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<UUID> DONE = new HashSet<>();

    static void addEvents(List<Runnable> pool, ServerWorld w, ServerPlayerEntity p, int stage) {
        pool.add(() -> speakTime(p));
        pool.add(() -> speakTime(p));
        if (stage >= 2) pool.add(() -> fakeError(p));
        if (stage >= 3) {
            pool.add(() -> knowsYou(p));
            pool.add(() -> glitchTitle(w, p));
        }
        if (stage >= 4) {
            pool.add(() -> fakeSave(p));
            pool.add(() -> knowsYou(p));
            if (!DONE.contains(p.getUuid())) pool.add(() -> finale(w, p));
        }
    }

    private static String now() { return LocalTime.now().format(FMT); }

    private static String user(ServerPlayerEntity p) {
        String u = System.getProperty("user.name");
        if (u == null || u.isBlank()) u = p.getName().getString();
        return u;
    }

    private static void say(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal("<Visitante> ").formatted(Formatting.DARK_RED)
            .append(Text.literal(msg).formatted(Formatting.GRAY)), false);
    }

    private static void log(ServerPlayerEntity p, String msg, Formatting color) {
        p.sendMessage(Text.literal(msg).formatted(color), false);
    }

    private static void speakTime(ServerPlayerEntity p) {
        int h = LocalTime.now().getHour();
        String hm = now();
        String[] lines;
        if (h < 5) {
            lines = new String[] {
                "são " + hm + ". por que você ainda está acordado?",
                "são " + hm + ". todo mundo já dormiu. só sobramos eu e você.",
                hm + ". a madrugada é minha."
            };
        } else if (h < 12) {
            lines = new String[] {
                hm + ". bom dia. dormiu bem? eu não deixei.",
                "já são " + hm + " e você está aqui comigo.",
                hm + ". de manhã eu fico mais perto."
            };
        } else if (h < 18) {
            lines = new String[] {
                hm + ". ainda é dia, mas eu já estou aqui.",
                "são " + hm + ". você tem certeza de que está sozinho?",
                hm + ". a luz do dia não me incomoda."
            };
        } else {
            lines = new String[] {
                "são " + hm + ". já escureceu aí fora?",
                hm + ". a noite está chegando. eu gosto dela.",
                "são " + hm + ". ninguém sabe que você está aqui, só eu."
            };
        }
        say(p, lines[RNG.nextInt(lines.length)]);
    }

    private static void knowsYou(ServerPlayerEntity p) {
        String u = user(p);
        String mc = p.getName().getString();
        String[] lines = new String[] {
            "eu sei o seu nome, " + u + ".",
            "você não é " + mc + ". você é " + u + ". eu sei.",
            "fechar o jogo não adianta, " + u + ".",
            "são " + now() + ", " + u + ". volta pra cá."
        };
        say(p, lines[RNG.nextInt(lines.length)]);
    }

    private static void fakeError(ServerPlayerEntity p) {
        int n = RNG.nextInt(3);
        if (n == 0) {
            log(p, "[Server thread/ERROR]: falha ao descarregar a entidade visitante:visitor", Formatting.RED);
            log(p, "[Server thread/ERROR]: a entidade se recusa a sair do mundo", Formatting.RED);
        } else if (n == 1) {
            log(p, "[Server thread/WARN]: level.dat modificado por um processo desconhecido", Formatting.YELLOW);
            log(p, "[Server thread/WARN]: processo desconhecido: visitante", Formatting.YELLOW);
        } else {
            log(p, "[Server thread/ERROR]: " + now() + " - conexão com o jogador perdida", Formatting.RED);
            log(p, "[Server thread/INFO]: ...mas você ainda está aí, não está?", Formatting.GRAY);
        }
    }

    private static void fakeSave(ServerPlayerEntity p) {
        log(p, "Salvando o mundo...", Formatting.GRAY);
        log(p, "[Server thread/ERROR]: o mundo não quer ser salvo.", Formatting.DARK_RED);
    }

    private static void glitchTitle(ServerWorld w, ServerPlayerEntity p) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(
            Text.literal("são " + now()).formatted(Formatting.DARK_RED)));
        p.networkHandler.sendPacket(new TitleS2CPacket(
            Text.literal("XXXXXXXX").formatted(Formatting.DARK_RED, Formatting.OBFUSCATED)));
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
            SoundCategory.AMBIENT, 2.0f, 1.0f);
    }

    /** O fim: uma sequencia de mensagens falsas e depois desconecta o jogador do mundo (so dentro do jogo). */
    static void finale(ServerWorld w, ServerPlayerEntity p) {
        if (!DONE.add(p.getUuid())) return;
        final String u = user(p);
        Infection.atLeast(2);

        Director.schedule(w, 0, () -> {
            log(p, "[Server thread/ERROR]: " + now() + " - acesso não autorizado ao mundo", Formatting.RED);
            log(p, "[Server thread/ERROR]: origem: visitante", Formatting.RED);
        });
        Director.schedule(w, 50, () -> {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 400, 0, false, false));
            for (int i = 0; i < 6; i++) {
                final int d = i * 14;
                Director.schedule(w, 50 + d, () -> w.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 2.0f, 1.0f));
            }
        });
        Director.schedule(w, 90, () -> log(p, "[Server thread/WARN]: o arquivo do mundo está sendo reescrito", Formatting.YELLOW));
        Director.schedule(w, 130, () -> say(p, "não adianta fechar, " + u + "."));
        Director.schedule(w, 170, () -> {
            Director.openDoors(w, p, 6, false);
            Director.snuffTorches(w, p);
        });
        Director.schedule(w, 200, () -> glitchTitle(w, p));
        Director.schedule(w, 250, () -> say(p, "são " + now() + ". eu já estou em tudo."));
        Director.schedule(w, 310, () -> {
            Infection.raise();
            p.networkHandler.disconnect(Text.literal(
                "Conexão encerrada.\n\nSão " + now() + ". " + u + ", ele disse que você pode voltar.\nEle vai estar esperando."));
        });
    }
}
