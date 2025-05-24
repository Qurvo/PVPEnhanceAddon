package com.pvpenhance.addon.modules.mace;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.Vec3d;

public class WindBurstManagement extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Double> minFallDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-fall-distance")
        .description("Minimum fall distance to optimize timing.")
        .defaultValue(3.0)
        .min(1.0)
        .sliderMax(10.0)
        .build()
    );
    
    private final Setting<Boolean> chainBounces = sgGeneral.add(new BoolSetting.Builder()
        .name("chain-bounces")
        .description("Automatically chain Wind Burst bounces.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Integer> maxChainLength = sgGeneral.add(new IntSetting.Builder()
        .name("max-chain-length")
        .description("Maximum number of bounces to chain.")
        .defaultValue(5)
        .min(1)
        .sliderMax(10)
        .visible(chainBounces::get)
        .build()
    );

    private double fallStartY = -1;
    private int bounceCount = 0;
    private boolean isChaining = false;
    private LivingEntity chainTarget;

    public WindBurstManagement() {
        super(Categories.Combat, "wind-burst-management", "Optimizes Wind Burst enchantment timing and chaining.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        
        // Reset when on ground
        if (mc.player.isOnGround() && !isChaining) {
            reset();
            return;
        }
        
        // Initialize fall tracking
        if (fallStartY == -1 && !mc.player.isOnGround()) {
            fallStartY = mc.player.getY();
        }
        
        double currentFallDistance = fallStartY - mc.player.getY();
        
        // Check if we should start managing Wind Burst
        if (currentFallDistance >= minFallDistance.get() && !isChaining) {
            LivingEntity target = findNearestTarget();
            if (target != null && hasMaceEquipped()) {
                startWindBurstSequence(target);
            }
        }
        
        // Manage chaining if active
        if (isChaining && chainBounces.get()) {
            manageChaining();
        }
    }
    
    private void startWindBurstSequence(LivingEntity target) {
        this.isChaining = true;
        this.chainTarget = target;
        this.bounceCount = 0;
    }
    
    private void manageChaining() {
        if (chainTarget == null || !chainTarget.isAlive() || bounceCount >= maxChainLength.get()) {
            stopChaining();
            return;
        }
        
        // Check if we're in bounce state (recently hit and launched upward)
        if (mc.player.getVelocity().y > 0.5 && bounceCount > 0) {
            // Look for next target while airborne
            LivingEntity nextTarget = findNearestTarget();
            if (nextTarget != null && nextTarget != chainTarget) {
                chainTarget = nextTarget;
            }
        }
        
        // Increment bounce count when we land a hit (detected by velocity change)
        Vec3d velocity = mc.player.getVelocity();
        if (velocity.y > 2.0 && mc.player.age % 20 == 0) { // Rough detection of Wind Burst activation
            bounceCount++;
        }
    }
    
    private void stopChaining() {
        this.isChaining = false;
        this.chainTarget = null;
        this.bounceCount = 0;
    }
    
    private LivingEntity findNearestTarget() {
        double range = 6.0;
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, 
                mc.player.getBoundingBox().expand(range), this::isValidTarget)) {
            
            double distance = mc.player.distanceTo(entity);
            if (distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }
        
        return closest;
    }
    
    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        return entity.isAlive() && !entity.isRemoved();
    }
    
    private boolean hasMaceEquipped() {
        return mc.player.getMainHandStack().getItem() == Items.MACE;
    }
    
    private void reset() {
        fallStartY = -1;
        bounceCount = 0;
        isChaining = false;
        chainTarget = null;
    }
}