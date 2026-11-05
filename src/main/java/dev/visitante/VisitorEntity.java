package dev.visitante;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class VisitorEntity extends HostileEntity {
    private int stage = 1;
    private int lifetime = 20 * 90;
    private int seenTicks = 0;
    private int unseenTicks = 0;
    private int attackCooldown = 0;

    public VisitorEntity(EntityType<? extends VisitorEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createVisitorAttributes() {
        return HostileEntity.createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setStage(int s) {
        this.stage = s;
        this.lifetime = 20 * (60 + 30 * s);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        ServerWorld sw = (ServerWorld) getWorld();
        PlayerEntity p = sw.getClosestPlayer(this, 160.0);
        if (p == null || age > lifetime) {
            discard();
            return;
        }
        float dist = distanceTo(p);
        boolean seen = isSeenBy(p);
        if (seen) { seenTicks++; unseenTicks = 0; } else { unseenTicks++; seenTicks = 0; }
        if (attackCooldown > 0) attackCooldown--;

        if (dist < 45) {
            int period = Math.max(10, (int) (dist * 1.1));
            if (age % period == 0) {
                float vol = Math.min(2.0f, 8.0f / Math.max(dist, 1f));
                sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.AMBIENT, vol, 1.0f);
            }
        }

        lookAtEntity(p, 30f, 30f);

        if (stage <= 2) {
            freeze();
            if (seenTicks > 60) discard();
        } else if (stage <= 4) {
            if (seen) {
                freeze();
                if (dist < 7 && seenTicks > 15) scareAndVanish(sw, p);
            } else if (dist > 4) {
                approach(p, 1.0);
            } else {
                freeze();
            }
        } else {
            if (seen) {
                freeze();
            } else {
                if (unseenTicks > 100 && dist > 14) hop(sw, p);
                else if (dist > 1.8) approach(p, 1.35);
                if (dist < 2.2 && attackCooldown == 0) attack(sw, p);
            }
        }
    }

    private void freeze() {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
    }

    private void approach(PlayerEntity p, double speed) {
        if (age % 5 == 0) getNavigation().startMovingTo(p, speed);
    }

    private boolean isSeenBy(PlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d to = new Vec3d(getX() - p.getX(), getEyeY() - p.getEyeY(), getZ() - p.getZ());
        double len = to.length();
        if (len < 0.001) return true;
        double dot = look.dotProduct(to.multiply(1.0 / len));
        return dot > 0.55 && p.canSee(this);
    }

    private void scareAndVanish(ServerWorld sw, PlayerEntity p) {
        sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST,
            SoundCategory.HOSTILE, 1.5f, 1.0f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, false));
        discard();
    }

    private void hop(ServerWorld sw, PlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() < 1e-4) return;
        flat = flat.normalize();
        BlockPos target = new BlockPos((int) Math.floor(p.getX() - flat.x * 10), p.getBlockY(),
            (int) Math.floor(p.getZ() - flat.z * 10));
        BlockPos spot = Director.findSpot(sw, target, 0, 3);
        if (spot != null) {
            refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, getYaw(), 0);
            getNavigation().stop();
            unseenTicks = 0;
        }
    }

    private void attack(ServerWorld sw, PlayerEntity p) {
        p.damage(sw.getDamageSources().mobAttack(this), 6.0f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0, false, false));
        sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT,
            SoundCategory.HOSTILE, 1.5f, 0.7f);
        attackCooldown = 120;
        retreat(sw, p);
    }

    private void retreat(ServerWorld sw, PlayerEntity p) {
        BlockPos spot = Director.findSpot(sw, p.getBlockPos(), 25, 40);
        if (spot != null) {
            refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, getYaw(), 0);
        }
        getNavigation().stop();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean r = super.damage(source, amount);
        if (r && !getWorld().isClient && isAlive() && getWorld() instanceof ServerWorld sw) {
            PlayerEntity p = sw.getClosestPlayer(this, 160.0);
            if (p != null) retreat(sw, p);
        }
        return r;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WARDEN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // silencioso
    }
}
