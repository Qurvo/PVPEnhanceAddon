package com.pvpenhance.addon.modules.elytra;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;

public class WindChargePearlBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> pearlSlot = sgGeneral.add(new IntSetting.Builder()
            .name("pearl-slot")
            .description("Hotbar slot containing ender pearls (1-9).")
            .defaultValue(3)
            .min(1)
            .sliderMax(9)
            .build()
    );

    private final Setting<Integer> windChargeSlot = sgGeneral.add(new IntSetting.Builder()
            .name("wind-charge-slot")
            .description("Hotbar slot containing wind charges (1-9).")
            .defaultValue(4)
            .min(1)
            .sliderMax(9)
            .build()
    );

    private final Setting<Integer> windChargeDelay = sgGeneral.add(new IntSetting.Builder()
            .name("wind-charge-delay")
            .description("Delay between pearl and wind charge in ticks.")
            .defaultValue(3)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private BoostState currentState = BoostState.THROW_PEARL;
    private int originalSlot = -1;
    private int sequenceTimer = 0;
    private float savedYaw;
    private float savedPitch;

    public WindChargePearlBoost() {
        super(Categories.Combat, "wind-charge-pearl-boost", "Throws pearl then wind charge in same direction for guaranteed hit.");
    }

    @Override
    public void onActivate() {
        reset();
        originalSlot = mc.player.getInventory().selectedSlot;

        // Save current look direction
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();

        currentState = BoostState.THROW_PEARL;
        sequenceTimer = 1; // Start immediately
        info("Starting wind charge pearl boost sequence");
    }

    @Override
    public void onDeactivate() {
        if (originalSlot != -1) {
            mc.player.getInventory().selectedSlot = originalSlot;
        }
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Handle sequence timing
        if (sequenceTimer > 0) {
            sequenceTimer--;
            return;
        }

        // Execute sequence steps
        switch (currentState) {
            case THROW_PEARL:
                throwPearl();
                break;

            case THROW_WIND_CHARGE:
                throwWindCharge();
                break;

            case FINISHED:
                this.toggle(); // Auto-disable
                break;
        }
    }

    private void throwPearl() {
        // Step 1: Throw pearl in current look direction

        // Switch to pearl slot
        int pearlSlotIndex = pearlSlot.get() - 1;
        mc.player.getInventory().selectedSlot = pearlSlotIndex;

        // Throw pearl
        mc.options.useKey.setPressed(true);

        // Release key after a tick
        new Thread(() -> {
            try {
                Thread.sleep(50);
                mc.options.useKey.setPressed(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        currentState = BoostState.THROW_WIND_CHARGE;
        sequenceTimer = windChargeDelay.get(); // Wait before throwing wind charge
    }

    private void throwWindCharge() {
        // Step 2: Throw wind charge in exact same direction

        // Restore exact same look direction
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);

        // Switch to wind charge slot
        int windChargeSlotIndex = windChargeSlot.get() - 1;
        mc.player.getInventory().selectedSlot = windChargeSlotIndex;

        // Throw wind charge
        mc.options.useKey.setPressed(true);

        // Release key and finish sequence
        new Thread(() -> {
            try {
                Thread.sleep(50);
                mc.options.useKey.setPressed(false);

                // Restore original slot
                if (originalSlot != -1) {
                    mc.player.getInventory().selectedSlot = originalSlot;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        info("Wind charge pearl boost completed!");
        currentState = BoostState.FINISHED;
    }

    private void reset() {
        currentState = BoostState.THROW_PEARL;
        originalSlot = -1;
        sequenceTimer = 0;
        savedYaw = 0;
        savedPitch = 0;
    }

    private enum BoostState {
        THROW_PEARL,
        THROW_WIND_CHARGE,
        FINISHED
    }
}