package com.pvpenhance.addon.modules.general;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class BetterAimAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgSmoothing = settings.createGroup("Smoothing");
    
    // Targeting Settings
    private final Setting<TargetMode> targetMode = sgTargeting.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("How to select initial target.")
        .defaultValue(TargetMode.ClosestDistance)
        .build()
    );
    
    private final Setting<Double> targetRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum targeting range.")
        .defaultValue(5.0)
        .min(2.0)
        .sliderMax(10.0)
        .build()
    );
    
    private final Setting<Double> unlockDistance = sgTargeting.add(new DoubleSetting.Builder()
        .name("unlock-distance")
        .description("Distance at which to unlock from target.")
        .defaultValue(8.0)
        .min(3.0)
        .sliderMax(15.0)
        .build()
    );
    
    private final Setting<Integer> blockTimeout = sgTargeting.add(new IntSetting.Builder()
        .name("block-timeout")
        .description("Ticks to wait before unlocking when target is behind blocks.")
        .defaultValue(100)
        .min(20)
        .sliderMax(200)
        .build()
    );
    
    // Smoothing Settings
    private final Setting<Double> aimSpeed = sgSmoothing.add(new DoubleSetting.Builder()
        .name("aim-speed")
        .description("How fast to move towards target.")
        .defaultValue(3.0)
        .min(0.5)
        .sliderMax(10.0)
        .build()
    );
    
    private final Setting<Double> smoothness = sgSmoothing.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("How smooth the aim movement should be.")
        .defaultValue(0.8)
        .min(0.1)
        .sliderMax(1.0)
        .build()
    );
    
    private final Setting<Boolean> prediction = sgSmoothing.add(new BoolSetting.Builder()
        .name("prediction")
        .description("Predict target movement.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Double> predictionStrength = sgSmoothing.add(new DoubleSetting.Builder()
        .name("prediction-strength")
        .description("How much to predict target movement.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderMax(5.0)
        .visible(prediction::get)
        .build()
    );

    private LivingEntity currentTarget;
    private int blockedTicks = 0;
    private Vec3d lastTargetPos = Vec3d.ZERO;
    private float targetYaw, targetPitch;
    private boolean isLocked = false;

    public BetterAimAssist() {
        super(Categories.Combat, "better-aim-assist", "Advanced aim assist with sticky targeting.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            acquireTarget();
        }
    }

    @Override
    public void onDeactivate() {
        unlockTarget();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        
        // Check if we need to unlock current target
        if (currentTarget != null) {
            if (!isValidTarget(currentTarget) || 
                mc.player.distanceTo(currentTarget) > unlockDistance.get()) {
                unlockTarget();
                return;
            }
            
            // Check line of sight
            if (!mc.player.canSee(currentTarget)) {
                blockedTicks++;
                if (blockedTicks > blockTimeout.get()) {
                    unlockTarget();
                    return;
                }
            } else {
                blockedTicks = 0;
            }
        }
        
        // Acquire new target if we don't have one
        if (currentTarget == null) {
            acquireTarget();
        }
        
        // Aim at current target
        if (currentTarget != null && isLocked) {
            aimAtTarget();
        }
    }
    
    private void acquireTarget() {
        LivingEntity bestTarget = null;
        double bestScore = Double.MAX_VALUE;
        
        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, 
                mc.player.getBoundingBox().expand(targetRange.get()), this::isValidTarget)) {
            
            double score = calculateTargetScore(entity);
            if (score < bestScore) {
                bestTarget = entity;
                bestScore = score;
            }
        }
        
        if (bestTarget != null) {
            lockTarget(bestTarget);
        }
    }
    
    private double calculateTargetScore(LivingEntity entity) {
        double distance = mc.player.distanceTo(entity);
        
        if (targetMode.get() == TargetMode.ClosestDistance) {
            return distance;
        } else if (targetMode.get() == TargetMode.LowestHealth) {
            return entity.getHealth();
        }
        
        return distance; // Fallback
    }
    
    private void lockTarget(LivingEntity target) {
        this.currentTarget = target;
        this.isLocked = true;
        this.blockedTicks = 0;
        this.lastTargetPos = target.getPos();
        info("Locked onto " + target.getName().getString());
    }
    
    private void unlockTarget() {
        this.currentTarget = null;
        this.isLocked = false;
        this.blockedTicks = 0;
    }
    
    private void aimAtTarget() {
        if (currentTarget == null) return;
        
        Vec3d targetPos = getTargetPosition();
        
        // Calculate angles
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d diff = targetPos.subtract(playerPos);
        
        double distance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float newYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90f;
        float newPitch = (float) -Math.toDegrees(Math.atan2(diff.y, distance));
        
        // Normalize angles
        newYaw = MathHelper.wrapDegrees(newYaw);
        newPitch = MathHelper.clamp(newPitch, -90f, 90f);
        
        // Apply smoothing
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        
        float deltaYaw = MathHelper.wrapDegrees(newYaw - currentYaw);
        float deltaPitch = newPitch - currentPitch;
        
        // Apply speed and smoothing
        float speed = aimSpeed.get().floatValue();
        float smooth = (float) smoothness.get().floatValue();
        
        deltaYaw *= speed * 0.1f;
        deltaPitch *= speed * 0.1f;
        
        // Apply smoothing with some randomization
        deltaYaw *= smooth + (Math.random() * 0.1 - 0.05);
        deltaPitch *= smooth + (Math.random() * 0.1 - 0.05);
        
        // Set new rotation
        mc.player.setYaw(currentYaw + deltaYaw);
        mc.player.setPitch(currentPitch + deltaPitch);
        
        // Update last position for prediction
        lastTargetPos = currentTarget.getPos();
    }
    
    private Vec3d getTargetPosition() {
        Vec3d basePos = getEffectiveTargetPos();
        
        if (!prediction.get()) {
            return basePos;
        }
        
        // Calculate velocity
        Vec3d velocity = basePos.subtract(lastTargetPos);
        
        // Apply prediction
        double predStrength = predictionStrength.get();
        Vec3d predictedPos = basePos.add(velocity.multiply(predStrength));
        
        return predictedPos;
    }

    private Vec3d getEffectiveTargetPos() {
        Vec3d basePos = currentTarget.getPos();

        // Check if any hitbox expanding module is active
        try {
            Module hitboxModule = Modules.get().get("hitboxes");
            if (hitboxModule != null && hitboxModule.isActive()) {
                // Use expanded hitbox calculations
                return basePos.add(0, currentTarget.getHeight() / 2, 0);
            }
        } catch (Exception e) {
            // Hitbox module not found, use default
        }

        return basePos.add(0, currentTarget.getHeight() / 2, 0);
    }
    
    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive() || entity.isRemoved()) return false;
        if (entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        return true;
    }
    
    public enum TargetMode {
        ClosestDistance,
        LowestHealth
    }
}