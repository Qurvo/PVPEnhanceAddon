package com.pvpenhance.addon.modules.elytra;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.*;
import net.minecraft.util.Hand;

public class WindChargeFlightChain extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Double> activationHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("activation-height")
        .description("Height threshold to use wind charges.")
        .defaultValue(10.0)
        .min(5.0)
        .sliderMax(30.0)
        .build()
    );
    
    private final Setting<Integer> chainCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("chain-cooldown")
        .description("Cooldown between wind charge uses (ticks).")
        .defaultValue(30)
        .min(10)
        .sliderMax(100)
        .build()
    );
    
    private final Setting<Boolean> autoFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Use firework rockets as backup mobility.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> conserveWindCharges = sgGeneral.add(new BoolSetting.Builder()
        .name("conserve-wind-charges")
        .description("Only use wind charges when necessary.")
        .defaultValue(true)
        .build()
    );

    private int cooldownTicks = 0;
    private boolean wasFlying = false;
    private double lastY = 0;

    public WindChargeFlightChain() {
        super(Categories.Combat, "wind-charge-flight-chain", "Optimizes wind charge usage for maintaining aerial superiority.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        
        boolean isFlying = mc.player.isGliding();
        double currentY = mc.player.getY();
        
        // Track if we were flying
        if (isFlying && !wasFlying) {
            lastY = currentY;
        }
        
        wasFlying = isFlying;
        
        // Check if we should use wind charge
        if (shouldUseWindCharge(isFlying, currentY)) {
            useWindCharge();
        }
        
        // Check if we should use firework as backup
        if (autoFirework.get() && shouldUseFirework(isFlying, currentY)) {
            useFirework();
        }
    }
    
    private boolean shouldUseWindCharge(boolean isFlying, double currentY) {
        // Check cooldown
        if (cooldownTicks > 0) return false;
        
        // Must have wind charges
        if (!hasWindCharges()) return false;
        
        // Don't use if conserving and we have good altitude
        if (conserveWindCharges.get() && currentY > activationHeight.get() + 5) {
            return false;
        }
        
        // Use if falling and below activation height
        if (!isFlying && currentY < activationHeight.get()) {
            return true;
        }
        
        // Use if flying but losing altitude rapidly
        if (isFlying && (lastY - currentY) > 2.0) {
            return true;
        }
        
        return false;
    }
    
    private boolean shouldUseFirework(boolean isFlying, double currentY) {
        // Only use firework if flying and we don't have wind charges
        if (!isFlying || hasWindCharges()) return false;
        
        // Use if below critical height
        return currentY < activationHeight.get() - 5;
    }
    
    private void useWindCharge() {
        int windChargeSlot = findWindChargeSlot();
        if (windChargeSlot == -1) return;
        
        int originalSlot = mc.player.getInventory().selectedSlot;
        
        // Switch to wind charge
        mc.player.getInventory().selectedSlot = windChargeSlot;
        
        // Look down and use
        float originalPitch = mc.player.getPitch();
        mc.player.setPitch(90f);
        
        // Use wind charge
        mc.options.useKey.setPressed(true);
        
        // Schedule restoration
        mc.execute(() -> {
            mc.options.useKey.setPressed(false);
            mc.player.setPitch(originalPitch);
            mc.player.getInventory().selectedSlot = originalSlot;
        });
        
        cooldownTicks = chainCooldown.get();
        info("Used wind charge for flight");
    }
    
    private void useFirework() {
        int fireworkSlot = findFireworkSlot();
        if (fireworkSlot == -1) return;
        
        int originalSlot = mc.player.getInventory().selectedSlot;
        
        // Switch to firework
        mc.player.getInventory().selectedSlot = fireworkSlot;
        
        // Use firework
        mc.options.useKey.setPressed(true);
        
        // Schedule restoration
        mc.execute(() -> {
            mc.options.useKey.setPressed(false);
            mc.player.getInventory().selectedSlot = originalSlot;
        });
        
        cooldownTicks = 20; // Shorter cooldown for fireworks
        info("Used firework rocket");
    }
    
    private boolean hasWindCharges() {
        return findWindChargeSlot() != -1;
    }
    
    private int findWindChargeSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem().toString().toLowerCase().contains("wind_charge")) {
                return i;
            }
        }
        return -1;
    }
    
    private int findFireworkSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.FIREWORK_ROCKET) {
                return i;
            }
        }
        return -1;
    }
    
    private void reset() {
        cooldownTicks = 0;
        wasFlying = false;
        lastY = mc.player != null ? mc.player.getY() : 0;
    }
}