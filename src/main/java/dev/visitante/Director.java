package dev.visitante;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

public class Director {
    private static final Random RNG = new Random();
    private static final Map<UUID, Long> NEXT_EVENT = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_SLEEPING = new HashMap<>();
    private static final List<Scheduled> TASKS = new ArrayList<>();
    public static int forcedStage = 0;

    private record Scheduled(long tick, Runnable run) {}

    private static final SoundEvent[] BEHIND = {
        SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundEvents.ENTITY_ENDERMAN_AMBIENT,
        SoundEvents.ENTITY_SKELETON_AMBIENT, SoundEvents.BLOCK_CHEST_OPEN,
        SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSER,
        SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundEvents.ENTITY_PHANTOM_AMBIENT,
        SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT
    };

    private static final String[] LINES = {
        "olha pra tras", "eu nao estou sozinho aqui", "ele esta dentro da casa",
        "nao olha pela janela", "eu ouvi alguem andando", "por que a porta esta aberta?",
        "ele ja sabe onde eu durmo", "as tochas estavam acesas agora pouco", "tem alguem me olhando"
    };

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Director::tick);
    }

    public static void forceNow(ServerPlayerEntity p) { NEXT_EVENT.put(p.getUuid(), 0L); }

    public static void forceSpawn(ServerPlayerEntity p) { spawnVisitor(p.getServerWorld(), p, stageOf(p)); }

    static int stageOf(ServerPlayerEntity p) {
        if (forcedStage > 0) return forcedStage;
        long minutes = p.getServerWorld().getTime() / 1200L;
        if (minutes < 8) return 1;
        if (minutes < 20) return 2;
        if (minutes < 40) return 3;
        if (minutes < 70) return 4;
        return 5;
    }

    static void schedule(ServerWorld w, int delayTicks, Runnable r) {
        TASKS.add(new Scheduled(w.getServer().getTicks() + delayTicks, r));
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();

        List<Runnable> due = new ArrayList<>();
        Iterator<Scheduled> it = TASKS.iterator();
        while (it.hasNext()) {
            Scheduled s = it.next();
            if (s.tick() <= now) { due.add(s.run()); it.remove(); }
        }
        for (Runnable r : due) {
            try { r.run(); } catch (Exception ignored) { }
        }

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            ServerWorld w = p.getServerWorld();
            if (w.getRegistryKey() != World.OVERWORLD) continue;
            int stage = stageOf(p);

            boolean sleeping = p.isSleeping();
            boolean was = WAS_SLEEPING.getOrDefault(p.getUuid(), false);
            WAS_SLEEPING.put(p.getUuid(), sleeping);
            if (was && !sleeping) onWake(w, p, stage);

            long next = NEXT_EVENT.computeIfAbsent(p.getUuid(), k -> now + 500L);
            if (now >= next) {
                try { triggerEvent(w, p, stage); } catch (Exception ignored) { }
                long cd = 20L * (62 - stage * 10 + RNG.nextInt(40));
                NEXT_EVENT.put(p.getUuid(), now + cd);
            }
        }
    }

    private static void triggerEvent(ServerWorld w, ServerPlayerEntity p, int stage) {
        if (stage >= 4) Infection.atLeast(1);
        List<Runnable> pool = new ArrayList<>();
        pool.add(() -> soundBehind(w, p));
        Meta.addEvents(pool, w, p, stage);
        if (RNG.nextInt(6) == 0) pool.add(() -> fakeJoin(w, p));
        if (stage >= 2) {
            pool.add(() -> openDoors(w, p, 1, true));
            pool.add(() -> snuffTorches(w, p));
            pool.add(() -> knock(w, p));
        }
        if (stage >= 3) {
            pool.add(() -> echoSteps(w, p));
            pool.add(() -> typedAsYou(p));
            pool.add(() -> darknessPulse(w, p));
        }
        if (stage >= 2) {
            for (int i = 0; i < stage; i++) pool.add(() -> spawnVisitor(w, p, stage));
        }
        pool.get(RNG.nextInt(pool.size())).run();
    }

    private static void onWake(ServerWorld w, ServerPlayerEntity p, int stage) {
        if (stage < 2) return;
        schedule(w, 20, () -> {
            openDoors(w, p, 3, false);
            snuffTorches(w, p);
        });
        int chance = stage >= 5 ? 85 : 55;
        if (stage >= 3 && w.getDifficulty() != Difficulty.PEACEFUL && RNG.nextInt(100) < chance) {
            if (!w.getEntitiesByType(TypeFilter.instanceOf(VisitorEntity.class), e -> true).isEmpty()) return;
            BlockPos spot = findSpot(w, p.getBlockPos(), 2, 4);
            if (spot == null) return;
            VisitorEntity v = ModEntities.VISITOR.create(w);
            if (v == null) return;
            v.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, RNG.nextFloat() * 360f, 0);
            v.setStage(stage);
            w.spawnEntity(v);
        }
    }

    static Vec3d behind(PlayerEntity p, double d) {
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() < 1e-4) flat = new Vec3d(0, 0, 1);
        flat = flat.normalize();
        return p.getPos().add(-flat.x * d, 0, -flat.z * d);
    }

    static void soundBehind(ServerWorld w, ServerPlayerEntity p) {
        Vec3d at = behind(p, 6 + RNG.nextInt(8));
        SoundEvent s = BEHIND[RNG.nextInt(BEHIND.length)];
        w.playSound(null, at.x, p.getY(), at.z, s, SoundCategory.HOSTILE, 1.0f, 0.8f + RNG.nextFloat() * 0.3f);
    }

    private static void fakeJoin(ServerWorld w, ServerPlayerEntity p) {
        p.sendMessage(Text.translatable("multiplayer.player.joined", "Visitante").formatted(Formatting.YELLOW), false);
        schedule(w, 20 * (8 + RNG.nextInt(20)), () ->
            p.sendMessage(Text.translatable("multiplayer.player.left", "Visitante").formatted(Formatting.YELLOW), false));
    }

    private static void typedAsYou(ServerPlayerEntity p) {
        String line = LINES[RNG.nextInt(LINES.length)];
        p.sendMessage(Text.literal("<" + p.getName().getString() + "> " + line), false);
    }

    private static List<BlockPos> findDoors(ServerWorld w, BlockPos c, int rad, boolean onlyClosed) {
        List<BlockPos> doors = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(c.add(-rad, -4, -rad), c.add(rad, 4, rad))) {
            BlockState s = w.getBlockState(pos);
            if (s.isIn(BlockTags.WOODEN_DOORS) && s.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && (!onlyClosed || !s.get(DoorBlock.OPEN))) {
                doors.add(pos.toImmutable());
            }
        }
        return doors;
    }

    static void openDoors(ServerWorld w, ServerPlayerEntity p, int count, boolean fallbackSound) {
        List<BlockPos> doors = findDoors(w, p.getBlockPos(), 12, true);
        if (doors.isEmpty()) {
            if (fallbackSound) soundBehind(w, p);
            return;
        }
        Collections.shuffle(doors, RNG);
        for (int i = 0; i < Math.min(count, doors.size()); i++) {
            BlockPos d = doors.get(i);
            BlockState s = w.getBlockState(d);
            if (s.getBlock() instanceof DoorBlock door) {
                door.setOpen(null, w, s, d, true);
            }
        }
    }

    static void snuffTorches(ServerWorld w, ServerPlayerEntity p) {
        BlockPos c = p.getBlockPos();
        List<BlockPos> torches = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(c.add(-16, -6, -16), c.add(16, 6, 16))) {
            BlockState s = w.getBlockState(pos);
            if (s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)
                || s.isOf(Blocks.SOUL_TORCH) || s.isOf(Blocks.SOUL_WALL_TORCH)) {
                torches.add(pos.toImmutable());
            }
        }
        if (torches.isEmpty()) return;
        Collections.shuffle(torches, RNG);
        int n = Math.min(torches.size(), 3 + RNG.nextInt(4));
        for (int i = 0; i < n; i++) {
            BlockPos t = torches.get(i);
            w.setBlockState(t, Blocks.AIR.getDefaultState(), 3);
            w.playSound(null, t, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.8f, 1.0f);
        }
    }

    private static void knock(ServerWorld w, ServerPlayerEntity p) {
        List<BlockPos> doors = findDoors(w, p.getBlockPos(), 16, false);
        final double x, y, z;
        if (!doors.isEmpty()) {
            BlockPos d = doors.get(RNG.nextInt(doors.size()));
            x = d.getX() + 0.5; y = d.getY(); z = d.getZ() + 0.5;
        } else {
            Vec3d at = behind(p, 4);
            x = at.x; y = p.getY(); z = at.z;
        }
        int t = 20;
        for (int i = 0; i < 3; i++) {
            schedule(w, t, () -> w.playSound(null, x, y, z, SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
                SoundCategory.HOSTILE, 1.2f, 0.8f));
            t += 10 + RNG.nextInt(8);
        }
    }

    private static void echoSteps(ServerWorld w, ServerPlayerEntity p) {
        SoundEvent step = w.getBlockState(p.getBlockPos().down()).getSoundGroup().getStepSound();
        for (int i = 0; i < 7; i++) {
            final Vec3d at = behind(p, 7.0 - i);
            schedule(w, 10 + i * 9, () -> w.playSound(null, at.x, at.y, at.z, step,
                SoundCategory.HOSTILE, 1.0f, 0.9f));
        }
    }

    private static void darknessPulse(ServerWorld w, ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 160, 0, false, false));
        for (int i = 0; i < 4; i++) {
            schedule(w, i * 18, () -> w.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 2.0f, 1.0f));
        }
    }

    static void spawnVisitor(ServerWorld w, ServerPlayerEntity p, int stage) {
        if (w.getDifficulty() == Difficulty.PEACEFUL) return;
        if (!w.getEntitiesByType(TypeFilter.instanceOf(VisitorEntity.class), e -> true).isEmpty()) return;
        int minR, maxR;
        if (stage <= 2) { minR = 35; maxR = 55; }
        else if (stage == 3) { minR = 22; maxR = 40; }
        else if (stage == 4) { minR = 14; maxR = 28; }
        else { minR = 18; maxR = 32; }
        BlockPos spot = null;
        for (int i = 0; i < 8; i++) {
            BlockPos s = findSpot(w, p.getBlockPos(), minR, maxR);
            if (s != null && !inView(p, s)) { spot = s; break; }
        }
        if (spot == null) return;
        VisitorEntity v = ModEntities.VISITOR.create(w);
        if (v == null) return;
        v.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, RNG.nextFloat() * 360f, 0);
        v.setStage(stage);
        w.spawnEntity(v);
    }

    private static boolean inView(PlayerEntity p, BlockPos pos) {
        Vec3d to = new Vec3d(pos.getX() + 0.5 - p.getX(), pos.getY() + 1 - p.getEyeY(), pos.getZ() + 0.5 - p.getZ());
        if (to.lengthSquared() < 1e-4) return true;
        return p.getRotationVec(1.0f).dotProduct(to.normalize()) > 0.5;
    }

    static BlockPos findSpot(ServerWorld w, BlockPos origin, int minR, int maxR) {
        for (int i = 0; i < 30; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double r = minR + RNG.nextDouble() * (maxR - minR);
            int x = origin.getX() + (int) Math.round(Math.cos(ang) * r);
            int z = origin.getZ() + (int) Math.round(Math.sin(ang) * r);
            if (!w.isChunkLoaded(new BlockPos(x, origin.getY(), z))) continue;
            for (int dy = 6; dy >= -10; dy--) {
                BlockPos pos = new BlockPos(x, origin.getY() + dy, z);
                if (w.isOutOfHeightLimit(pos)) continue;
                BlockPos below = pos.down();
                if (w.getBlockState(pos).isAir() && w.getBlockState(pos.up()).isAir()
                    && w.getBlockState(pos.up(2)).isAir()
                    && w.getBlockState(below).isSolidBlock(w, below)
                    && !w.getBlockState(below).isIn(BlockTags.LEAVES)) {
                    return pos;
                }
            }
        }
        return null;
    }
}
