package com.pvpenhance.addon.modules.general;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class KeybindArmorSwitch extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> armorSlot = sgGeneral.add(new IntSetting.Builder()
            .name("armor-slot")
            .description("Hotbar slot containing elytra/chestplate (1-9).")
            .defaultValue(9)
            .min(1)
            .sliderMax(9)
            .build()
    );

    private boolean hasExecuted = false;

    public KeybindArmorSwitch() {
        super(Categories.Player, "keybind-armor-switch", "Quick hotbar armor switching with right-click.");
    }

    @Override
    public void onActivate() {
        hasExecuted = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.currentScreen != null || hasExecuted) return;

        performArmorSwitch();
        hasExecuted = true;

        // Disable module after executing
        this.toggle();
    }

    private void performArmorSwitch() {
        int originalSlot = mc.player.getInventory().selectedSlot;
        int targetSlot = armorSlot.get() - 1; // Convert to 0-based index

        // Switch to armor slot
        mc.player.getInventory().selectedSlot = targetSlot;

        // Right click to equip/use item
        mc.options.useKey.setPressed(true);

        // Schedule returning to original slot and releasing key after a short delay
        new Thread(() -> {
            try {
                Thread.sleep(50); // 50ms delay

                // Release right click
                mc.options.useKey.setPressed(false);

                // Return to original slot
                mc.player.getInventory().selectedSlot = originalSlot;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        info("Armor switched from slot " + armorSlot.get());
    }
}