package com.pvpenhance.addon.modules.shield;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public class WTapEnhancer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Integer> tapDuration = sgGeneral.add(new IntSetting.Builder()
        .name("tap-duration")
        .description("How long to release W key in ticks.")
        .defaultValue(2)
        .min(1)
        .sliderMax(8)
        .build()
    );
    
    private final Setting<Integer> tapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tap-delay")
        .description("Delay before starting W-tap in ticks.")
        .defaultValue(1)
        .min(0)
        .sliderMax(5)
        .build()
    );
    
    private final Setting<Boolean> onlyOnHit = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-hit")
        .description("Only W-tap when successfully hitting an entity.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> randomization = sgGeneral.add(new BoolSetting.Builder()
        .name("randomization")
        .description("Add slight randomization to timing.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Integer> randomRange = sgGeneral.add(new IntSetting.Builder()
        .name("random-range")
        .description("Range of randomization in ticks.")
        .defaultValue(2)
        .min(0)
        .sliderMax(5)
        .visible(randomization::get)
        .build()
    );

    private boolean isTapping = false;
    private int tapTicks = 0;
    private int delayTicks = 0;
    private boolean wasForwardPressed = false;

    public WTapEnhancer() {
        super(Categories.Combat, "w-tap-enhancer", "Automates W-tapping for better knockback during fights.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        if (isTapping) {
            // Restore forward key state
            mc.options.forwardKey.setPressed(wasForwardPressed);
        }
        reset();
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (!isValidTarget(event.entity)) return;
        
        // Don't start new tap if already tapping
        if (isTapping || delayTicks > 0) return;
        
        // Check if we should only tap on successful hits
        if (onlyOnHit.get()) {
            // Simple check - if we're close enough and entity is alive
            if (mc.player.distanceTo(event.entity) > 6.0) return;
        }
        
        startWTap();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        
        // Handle delay before tapping
        if (delayTicks > 0) {
            delayTicks--;
            if (delayTicks == 0) {
                executeWTap();
            }
            return;
        }
        
        // Handle active tapping
        if (isTapping) {
            tapTicks--;
            if (tapTicks <= 0) {
                endWTap();
            }
        }
    }
    
    private void startWTap() {
        // Store current forward key state
        wasForwardPressed = mc.options.forwardKey.isPressed();
        
        // Set delay with optional randomization
        int baseDelay = tapDelay.get();
        if (randomization.get()) {
            int variance = (int) (Math.random() * randomRange.get()) - (randomRange.get() / 2);
            baseDelay = Math.max(0, baseDelay + variance);
        }
        
        delayTicks = baseDelay;
        
        // If no delay, execute immediately
        if (delayTicks == 0) {
            executeWTap();
        }
    }
    
    private void executeWTap() {
        // Start the tap
        isTapping = true;
        
        // Calculate tap duration with optional randomization
        int duration = tapDuration.get();
        if (randomization.get()) {
            int variance = (int) (Math.random() * randomRange.get()) - (randomRange.get() / 2);
            duration = Math.max(1, duration + variance);
        }
        
        tapTicks = duration;
        
        // Release forward key
        mc.options.forwardKey.setPressed(false);
    }
    
    private void endWTap() {
        // Restore forward key state
        mc.options.forwardKey.setPressed(wasForwardPressed);
        
        // Reset state
        isTapping = false;
        tapTicks = 0;
    }
    
    private boolean isValidTarget(net.minecraft.entity.Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) return false;
        if (entity == mc.player) return false;
        if (entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        return livingEntity.isAlive() && !livingEntity.isRemoved();
    }
    
    private void reset() {
        isTapping = false;
        tapTicks = 0;
        delayTicks = 0;
        wasForwardPressed = false;
    }
}