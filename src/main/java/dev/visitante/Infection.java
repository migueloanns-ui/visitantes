package dev.visitante;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Estado simples salvo na pasta config do jogo. Nao toca em nada fora dela. */
public class Infection {
    public static volatile int level = 0;
    private static boolean loaded = false;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("visitante_state.properties");
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (Files.exists(f)) {
                Properties pr = new Properties();
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) { pr.load(r); }
                level = Math.max(0, Math.min(3, Integer.parseInt(pr.getProperty("level", "0").trim())));
            }
        } catch (Exception ignored) { }
    }

    private static void save() {
        try {
            Properties pr = new Properties();
            pr.setProperty("level", Integer.toString(level));
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) { pr.store(w, null); }
        } catch (IOException ignored) { }
    }

    public static synchronized void atLeast(int n) {
        load();
        if (level < n) { level = n; save(); }
    }

    public static synchronized void raise() {
        load();
        level = Math.min(3, level + 1);
        save();
    }

    public static synchronized void reset() {
        load();
        level = 0;
        save();
    }
}
