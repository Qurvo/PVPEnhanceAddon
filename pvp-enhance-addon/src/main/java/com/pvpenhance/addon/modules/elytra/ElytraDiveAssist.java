package com.pvpenhance.addon.modules.elytra;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.ColorSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.screen.slot.SlotActionType;

public class ElytraDiveAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("target-range")
            .description("Range to detect targets for dive assist.")
            .defaultValue(4.0)
            .min(2.0)
            .sliderMax(8.0)
            .build()
    );

    private final Setting<Double> minFallDistance = sgGeneral.add(new DoubleSetting.Builder()
            .name("min-fall-distance")
            .description("Minimum fall distance to trigger assist.")
            .defaultValue(3.0)
            .min(1.0)
            .sliderMax(10.0)
            .build()
    );

    private final Setting<Integer> chestplateSlot = sgGeneral.add(new IntSetting.Builder()
            .name("chestplate-slot")
            .description("Hotbar slot containing chestplate (1-9).")
            .defaultValue(8)
            .min(1)
            .sliderMax(9)
            .build()
    );

    private final Setting<Boolean> showTargetHighlight = sgRender.add(new BoolSetting.Builder()
            .name("show-target-highlight")
            .description("Highlight targets in range.")
            .defaultValue(true)
            .build()
    );

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> targetColor = sgRender.add(new ColorSetting.Builder()
            .name("target-color")
            .description("Color for target highlight.")
            .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 0, 0, 150))
            .build()
    );

    private LivingEntity currentTarget;
    private boolean hasExecuted = false;
    private double fallStartY = -1;
    private int originalSlot = -1;

    public ElytraDiveAssist() {
        super(Categories.Combat, "elytra-dive-assist", "Auto-equips chestplate and switches to mace when diving near targets.");
    }

    @Override
    public void onActivate() {
        reset();
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

        // Reset when on ground
        if (mc.player.isOnGround()) {
            if (hasExecuted) {
                reset();
            }
            return;
        }

        // Track fall distance
        if (fallStartY == -1) {
            fallStartY = mc.player.getY();
        }

        double currentFallDistance = fallStartY - mc.player.getY();

        // Check if we should execute
        if (!hasExecuted && currentFallDistance >= minFallDistance.get()) {
            currentTarget = findNearestTarget();

            if (currentTarget != null) {
                executeDiveAssist();
                hasExecuted = true;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!this.isActive() || !showTargetHighlight.get()) return;

        if (currentTarget != null && !hasExecuted) {
            Vec3d pos = currentTarget.getPos();
            event.renderer.box(pos.x - 0.6, pos.y, pos.z - 0.6,
                    pos.x + 0.6, pos.y + currentTarget.getHeight(), pos.z + 0.6,
                    targetColor.get(), targetColor.get(), ShapeMode.Lines, 0);
        }
    }

    private void executeDiveAssist() {
        // Store original slot
        originalSlot = mc.player.getInventory().selectedSlot;

        // Equip chestplate from hotbar
        int chestSlot = chestplateSlot.get() - 1; // Convert to 0-based index
        swapToChestplate(chestSlot);

        // Switch to mace
        int maceSlot = findMaceSlot();
        if (maceSlot != -1) {
            mc.player.getInventory().selectedSlot = maceSlot;
        }

        // Attack target
        if (currentTarget != null && mc.player.distanceTo(currentTarget) <= targetRange.get()) {
            mc.interactionManager.attackEntity(mc.player, currentTarget);
        }

        info("Dive assist executed on " + (currentTarget != null ? currentTarget.getName().getString() : "target"));
    }

    private void swapToChestplate(int hotbarSlot) {
        // Swap chestplate from hotbar to armor slot
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                36 + hotbarSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                6, 0, SlotActionType.PICKUP, mc.player); // Chest armor slot
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                36 + hotbarSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    private LivingEntity findNearestTarget() {
        double range = targetRange.get();
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

    private int findMaceSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.MACE) {
                return i;
            }
        }
        return -1;
    }

    private void reset() {
        currentTarget = null;
        hasExecuted = false;
        fallStartY = -1;
        originalSlot = -1;
    }
}