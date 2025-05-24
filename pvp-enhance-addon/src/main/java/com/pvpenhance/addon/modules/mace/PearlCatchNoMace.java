package com.pvpenhance.addon.modules.mace;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class PearlCatchNoMace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> pearlSlot = sgGeneral.add(new IntSetting.Builder()
            .name("pearl-slot")
            .description("Hotbar slot containing ender pearls (1-9).")
            .defaultValue(3)
            .min(1)
            .sliderMax(9)
            .build()
    );

    private final Setting<Double> maxRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("max-range")
            .description("Maximum range to find targets.")
            .defaultValue(15.0)
            .min(5.0)
            .sliderMax(30.0)
            .build()
    );

    private boolean hasExecuted = false;

    public PearlCatchNoMace() {
        super(Categories.Combat, "pearl-catch-no-mace", "Throws pearl at crosshair target, then disables.");
    }

    @Override
    public void onActivate() {
        hasExecuted = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (!hasExecuted) {
            LivingEntity target = findCrosshairTarget();
            if (target != null) {
                executePearlThrow(target);
            } else {
                info("No valid target found");
            }
            hasExecuted = true;
        }

        // Auto-disable after execution
        if (hasExecuted) {
            this.toggle();
        }
    }

    private void executePearlThrow(LivingEntity target) {
        int originalSlot = mc.player.getInventory().selectedSlot;

        // Switch to pearl slot
        int pearlSlotIndex = pearlSlot.get() - 1;
        mc.player.getInventory().selectedSlot = pearlSlotIndex;

        // Aim at target with prediction
        aimAtTarget(target);

        // Throw pearl
        mc.options.useKey.setPressed(true);

        // Schedule restoration and key release
        new Thread(() -> {
            try {
                Thread.sleep(50);
                mc.options.useKey.setPressed(false);

                // Switch back to original slot
                mc.player.getInventory().selectedSlot = originalSlot;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        info("Pearl thrown at " + target.getName().getString());
    }

    private LivingEntity findCrosshairTarget() {
        // First check what we're directly looking at
        LivingEntity directTarget = getDirectLookTarget();
        if (directTarget != null) {
            return directTarget;
        }

        // If not looking directly at anyone, find closest to crosshair
        return getClosestToCrosshair();
    }

    private LivingEntity getDirectLookTarget() {
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hitResult;
            if (entityHit.getEntity() instanceof LivingEntity target && isValidTarget(target)) {
                return target;
            }
        }
        return null;
    }

    private LivingEntity getClosestToCrosshair() {
        double range = maxRange.get();
        LivingEntity closest = null;
        double smallestAngle = Double.MAX_VALUE;

        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class,
                mc.player.getBoundingBox().expand(range), this::isValidTarget)) {

            double angle = getAngleToCrosshair(entity);
            if (angle < smallestAngle && angle < 30.0) { // Within 30 degrees of crosshair
                closest = entity;
                smallestAngle = angle;
            }
        }

        return closest;
    }

    private double getAngleToCrosshair(LivingEntity entity) {
        // Calculate direction to entity
        double dx = entity.getX() - mc.player.getX();
        double dy = entity.getY() + entity.getHeight()/2 - mc.player.getEyeY();
        double dz = entity.getZ() - mc.player.getZ();

        double distance = Math.sqrt(dx * dx + dz * dz);

        // Calculate target angles
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float targetPitch = (float) Math.toDegrees(Math.atan2(-dy, distance));

        // Normalize angles
        targetYaw = normalizeAngle(targetYaw);
        float currentYaw = normalizeAngle(mc.player.getYaw());
        float currentPitch = mc.player.getPitch();

        // Calculate angle differences
        float yawDiff = Math.abs(targetYaw - currentYaw);
        if (yawDiff > 180) yawDiff = 360 - yawDiff;

        float pitchDiff = Math.abs(targetPitch - currentPitch);

        // Return combined angle difference
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle < 0) angle += 360;
        return angle;
    }

    private void aimAtTarget(LivingEntity target) {
        // Calculate direction with velocity prediction
        double dx = target.getX() - mc.player.getX();
        double dy = target.getY() + target.getHeight()/2 - mc.player.getEyeY();
        double dz = target.getZ() - mc.player.getZ();

        // Add velocity prediction
        dx += target.getVelocity().x * 8; // Lead time
        dy += target.getVelocity().y * 8;
        dz += target.getVelocity().z * 8;

        double distance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, distance));

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        return entity.isAlive() && !entity.isRemoved();
    }
}