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

public class WeaponSwapOptimizer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> detectionRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("detection-range")
            .description("Range to detect shield users.")
            .defaultValue(6.0)
            .min(3.0)
            .sliderMax(10.0)
            .build()
    );

    private final Setting<Integer> swordSlot = sgGeneral.add(new IntSetting.Builder()
            .name("sword-slot")
            .description("Hotbar slot containing your sword (1-9).")
            .defaultValue(1)
            .min(1)
            .sliderMax(9)
            .build()
    );

    private final Setting<Integer> axeSlot = sgGeneral.add(new IntSetting.Builder()
            .name("axe-slot")
            .description("Hotbar slot containing your axe (1-9).")
            .defaultValue(2)
            .min(1)
            .sliderMax(9)
            .build()
    );

    private final Setting<Boolean> onlyWhenTargeting = sgGeneral.add(new BoolSetting.Builder()
            .name("only-when-targeting")
            .description("Only swap when actively targeting a shield user.")
            .defaultValue(true)
            .build()
    );

    private int swapCooldown = 0;

    public WeaponSwapOptimizer() {
        super(Categories.Combat, "weapon-swap-optimizer", "Performs attribute swap from sword to axe when opponent raises shield.");
    }

    @Override
    public void onActivate() {
        swapCooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (swapCooldown > 0) {
            swapCooldown--;
            return;
        }

        // Find shield user
        LivingEntity target = findShieldUser();
        if (target == null) return;

        // Check if we should only swap when actively targeting
        if (onlyWhenTargeting.get() && !isLookingAtEntity(target)) {
            return;
        }

        // Perform attribute swap: sword -> axe -> attack -> sword
        performAttributeSwap(target);
    }

    private void performAttributeSwap(LivingEntity target) {
        int swordSlotIndex = swordSlot.get() - 1; // Convert to 0-based
        int axeSlotIndex = axeSlot.get() - 1; // Convert to 0-based

        // Step 1: Switch to sword (to set up the swap)
        mc.player.getInventory().selectedSlot = swordSlotIndex;

        // Step 2: Immediately switch to axe
        mc.player.getInventory().selectedSlot = axeSlotIndex;

        // Step 3: Attack immediately (this exploits the attribute swap timing)
        mc.interactionManager.attackEntity(mc.player, target);

        // Step 4: Switch back to sword
        mc.player.getInventory().selectedSlot = swordSlotIndex;

        // Set cooldown to prevent spam
        swapCooldown = 10; // Half second cooldown

        info("Attribute swapped on " + target.getName().getString());
    }

    private LivingEntity findShieldUser() {
        double range = detectionRange.get();
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class,
                mc.player.getBoundingBox().expand(range), this::isValidTarget)) {

            if (!isEntityBlocking(entity)) continue;

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

    private boolean isEntityBlocking(LivingEntity entity) {
        return entity.isBlocking() && entity.getActiveItem().getItem() instanceof ShieldItem;
    }

    private boolean isLookingAtEntity(LivingEntity entity) {
        // Simple check if player is looking roughly towards the entity
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 0.1) return true;

        // Normalize direction
        dx /= distance;
        dz /= distance;

        // Get player's look direction
        double yaw = Math.toRadians(mc.player.getYaw());
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);

        // Calculate dot product
        double dot = dx * lookX + dz * lookZ;

        // Return true if looking within ~60 degrees
        return dot > 0.5;
    }
}