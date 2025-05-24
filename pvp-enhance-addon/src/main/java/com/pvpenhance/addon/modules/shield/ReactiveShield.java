package com.pvpenhance.addon.modules.shield;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Hand;

public class ReactiveShield extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> blockProjectiles = sgGeneral.add(new BoolSetting.Builder()
            .name("block-projectiles")
            .description("Automatically block incoming projectiles.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> blockMelee = sgGeneral.add(new BoolSetting.Builder()
            .name("block-melee")
            .description("Automatically block incoming melee attacks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> reactionTime = sgGeneral.add(new DoubleSetting.Builder()
            .name("reaction-time")
            .description("Simulated reaction time in milliseconds.")
            .defaultValue(150.0)
            .min(50.0)
            .sliderMax(500.0)
            .build()
    );

    private final Setting<Double> projectileRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("projectile-range")
            .description("Range to detect incoming projectiles.")
            .defaultValue(10.0)
            .min(5.0)
            .sliderMax(20.0)
            .build()
    );

    private final Setting<Double> meleeRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("melee-range")
            .description("Range to detect melee attackers.")
            .defaultValue(6.0)
            .min(3.0)
            .sliderMax(10.0)
            .build()
    );

    private final Setting<Integer> blockDuration = sgGeneral.add(new IntSetting.Builder()
            .name("block-duration")
            .description("How long to hold block in ticks.")
            .defaultValue(20)
            .min(5)
            .sliderMax(40)
            .build()
    );

    private boolean isBlocking = false;
    private int blockTimer = 0;
    private int reactionDelay = 0;

    public ReactiveShield() {
        super(Categories.Combat, "reactive-shield", "Blocks threats for short duration then releases.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        if (isBlocking) {
            stopBlocking();
        }
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (!hasShield()) {
            if (isBlocking) stopBlocking();
            return;
        }

        // Handle blocking timer
        if (isBlocking) {
            blockTimer--;
            if (blockTimer <= 0) {
                stopBlocking();
            }
            return;
        }

        if (reactionDelay > 0) {
            reactionDelay--;
            return;
        }

        boolean shouldBlock = false;

        // Check for incoming projectiles
        if (blockProjectiles.get() && hasIncomingProjectile()) {
            shouldBlock = true;
        }

        // Check for nearby melee attackers
        if (blockMelee.get() && hasMeleeAttacker()) {
            shouldBlock = true;
        }

        if (shouldBlock) {
            startBlocking();
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof ProjectileEntity projectile)) return;
        if (!blockProjectiles.get() || reactionDelay > 0) return;

        // Check if projectile is targeting us
        if (isProjectileTargetingPlayer(projectile)) {
            // Add reaction delay
            reactionDelay = (int) (reactionTime.get() / 50); // Convert ms to ticks
        }
    }

    private void startBlocking() {
        if (!hasShield() || isBlocking) return;

        mc.options.useKey.setPressed(true);
        isBlocking = true;
        blockTimer = blockDuration.get();

        // Add small randomization to make it look more human
        int variance = (int) (Math.random() * 3 + 1);
        reactionDelay = variance;

        info("Blocking threat");
    }

    private void stopBlocking() {
        mc.options.useKey.setPressed(false);
        isBlocking = false;
        blockTimer = 0;
    }

    private boolean hasIncomingProjectile() {
        double range = projectileRange.get();

        for (ProjectileEntity projectile : mc.world.getEntitiesByClass(ProjectileEntity.class,
                mc.player.getBoundingBox().expand(range), p -> true)) {

            if (isProjectileTargetingPlayer(projectile)) {
                return true;
            }
        }

        return false;
    }

    private boolean isProjectileTargetingPlayer(ProjectileEntity projectile) {
        // Skip projectiles from friends
        if (projectile.getOwner() instanceof PlayerEntity owner && Friends.get().isFriend(owner)) {
            return false;
        }

        // Simple trajectory calculation
        double distance = mc.player.distanceTo(projectile);
        if (distance > projectileRange.get()) return false;

        // Check if projectile is moving towards player
        double dx = mc.player.getX() - projectile.getX();
        double dz = mc.player.getZ() - projectile.getZ();
        double vx = projectile.getVelocity().x;
        double vz = projectile.getVelocity().z;

        // Dot product to check direction
        double dot = dx * vx + dz * vz;
        return dot < 0; // Negative means moving towards player
    }

    private boolean hasMeleeAttacker() {
        double range = meleeRange.get();

        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class,
                mc.player.getBoundingBox().expand(range), this::isValidAttacker)) {

            // Check if entity is looking at us and close enough
            if (isEntityLookingAtPlayer(entity) && entity.distanceTo(mc.player) <= 5.0) {
                return true;
            }
        }

        return false;
    }

    private boolean isValidAttacker(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        return entity.isAlive() && !entity.isRemoved();
    }

    private boolean isEntityLookingAtPlayer(LivingEntity entity) {
        // Simple line of sight check
        double dx = mc.player.getX() - entity.getX();
        double dz = mc.player.getZ() - entity.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 0.1) return true;

        // Normalize direction vector
        dx /= distance;
        dz /= distance;

        // Get entity's look direction
        double yaw = Math.toRadians(entity.getYaw());
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);

        // Calculate dot product (cosine of angle)
        double dot = dx * lookX + dz * lookZ;

        // Return true if entity is looking roughly towards player (within 45 degrees)
        return dot > 0.7;
    }

    private boolean hasShield() {
        return mc.player.getOffHandStack().getItem() instanceof ShieldItem ||
                mc.player.getMainHandStack().getItem() instanceof ShieldItem;
    }

    private void reset() {
        isBlocking = false;
        blockTimer = 0;
        reactionDelay = 0;
    }
}