package com.pvpenhance.addon.modules.shield;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;

public class ShieldCounterAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Integer> counterDelay = sgGeneral.add(new IntSetting.Builder()
        .name("counter-delay")
        .description("Delay after blocking before counter-attacking (ticks).")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build()
    );
    
    private final Setting<Integer> counterWindow = sgGeneral.add(new IntSetting.Builder()
        .name("counter-window")
        .description("Time window for counter-attack after blocking (ticks).")
        .defaultValue(10)
        .min(5)
        .sliderMax(20)
        .build()
    );
    
    private final Setting<Double> counterRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("counter-range")
        .description("Range for counter-attacks.")
        .defaultValue(4.5)
        .min(2.0)
        .sliderMax(6.0)
        .build()
    );
    
    private final Setting<Boolean> autoLowerShield = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-lower-shield")
        .description("Automatically lower shield for counter-attacks.")
        .defaultValue(true)
        .build()
    );

    private boolean wasBlocking = false;
    private int blockEndTime = -1;
    private LivingEntity lastAttacker = null;
    private int counterCooldown = 0;

    public ShieldCounterAssist() {
        super(Categories.Combat, "shield-counter-assist", "Semi-automated counter-attacks after successful blocks.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        
        if (counterCooldown > 0) {
            counterCooldown--;
        }
        
        boolean currentlyBlocking = isPlayerBlocking();
        
        // Detect when player stops blocking (potential counter opportunity)
        if (wasBlocking && !currentlyBlocking) {
            blockEndTime = mc.player.age;
            lastAttacker = findNearestAttacker();
        }
        
        wasBlocking = currentlyBlocking;
        
        // Check for counter-attack opportunity
        if (shouldCounterAttack()) {
            executeCounterAttack();
        }
    }
    
    private boolean shouldCounterAttack() {
        // Must have recently stopped blocking
        if (blockEndTime == -1) return false;
        
        // Check if we're in the counter window
        int timeSinceBlock = mc.player.age - blockEndTime;
        if (timeSinceBlock < counterDelay.get() || timeSinceBlock > counterWindow.get()) {
            return false;
        }
        
        // Must have a valid target
        if (lastAttacker == null || !lastAttacker.isAlive()) {
            return false;
        }
        
        // Check range
        if (mc.player.distanceTo(lastAttacker) > counterRange.get()) {
            return false;
        }
        
        // Check cooldown
        return counterCooldown <= 0;
    }
    
    private void executeCounterAttack() {
        if (lastAttacker == null) return;
        
        // Lower shield if auto-lower is enabled
        if (autoLowerShield.get() && isPlayerBlocking()) {
            mc.options.useKey.setPressed(false);
        }
        
        // Attack the target
        mc.interactionManager.attackEntity(mc.player, lastAttacker);
        
        // Set cooldown and reset state
        counterCooldown = 20; // 1 second cooldown
        blockEndTime = -1;
        lastAttacker = null;
        
        info("Counter-attacked!");
    }
    
    private boolean isPlayerBlocking() {
        return mc.player.isBlocking() && hasShieldEquipped();
    }
    
    private boolean hasShieldEquipped() {
        return mc.player.getOffHandStack().getItem() instanceof ShieldItem ||
               mc.player.getMainHandStack().getItem() instanceof ShieldItem;
    }
    
    private LivingEntity findNearestAttacker() {
        double range = counterRange.get() + 2.0; // Slightly larger range for detection
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, 
                mc.player.getBoundingBox().expand(range), this::isValidAttacker)) {
            
            // Check if entity is looking at us (likely attacking)
            if (!isEntityLookingAtPlayer(entity)) continue;
            
            double distance = mc.player.distanceTo(entity);
            if (distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }
        
        return closest;
    }
    
    private boolean isValidAttacker(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        return entity.isAlive() && !entity.isRemoved();
    }
    
    private boolean isEntityLookingAtPlayer(LivingEntity entity) {
        double dx = mc.player.getX() - entity.getX();
        double dz = mc.player.getZ() - entity.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        if (distance < 0.1) return true;
        
        dx /= distance;
        dz /= distance;
        
        double yaw = Math.toRadians(entity.getYaw());
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);
        
        double dot = dx * lookX + dz * lookZ;
        return dot > 0.7; // Stricter angle for attackers
    }
    
    private void reset() {
        wasBlocking = false;
        blockEndTime = -1;
        lastAttacker = null;
        counterCooldown = 0;
    }
}