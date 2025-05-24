package com.pvpenhance.addon.modules.mace;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.Box;

public class ForceMaceModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgShieldSlam = settings.createGroup("Shield Slam");

    private final Setting<Double> minFallDistance = sgGeneral.add(new DoubleSetting.Builder()
            .name("min-fall-distance")
            .description("Minimum fall distance to trigger mace switch.")
            .defaultValue(2.5)
            .min(1.0)
            .sliderMax(10.0)
            .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("target-range")
            .description("Range to search for targets.")
            .defaultValue(5.0)
            .min(2.0)
            .sliderMax(8.0)
            .build()
    );

    private final Setting<Boolean> autoAttack = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-attack")
            .description("Automatically attack with mace when in range.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
            .name("switch-back")
            .description("Switch back to previous weapon after landing.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> enableShieldSlam = sgShieldSlam.add(new BoolSetting.Builder()
            .name("enable-shield-slam")
            .description("Automatically detect shield users and perform axe->mace combo.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> axeSlot = sgShieldSlam.add(new IntSetting.Builder()
            .name("axe-slot")
            .description("Hotbar slot containing your axe (1-9).")
            .defaultValue(2)
            .min(1)
            .sliderMax(9)
            .visible(enableShieldSlam::get)
            .build()
    );

    private final Setting<Integer> maceSlot = sgShieldSlam.add(new IntSetting.Builder()
            .name("mace-slot")
            .description("Hotbar slot containing your mace (1-9).")
            .defaultValue(9)
            .min(1)
            .sliderMax(9)
            .visible(enableShieldSlam::get)
            .build()
    );

    private final Setting<Boolean> debugMessages = sgShieldSlam.add(new BoolSetting.Builder()
            .name("debug-messages")
            .description("Show debug messages in chat.")
            .defaultValue(false)
            .visible(enableShieldSlam::get)
            .build()
    );

    private double fallStartY = -1;
    private int originalSlot = -1;
    private boolean hasSwitched = false;
    private int attackCooldown = 0;
    private LivingEntity lockedTarget = null;
    private boolean hasAttacked = false;

    // Shield slam state
    private AttackMode currentMode = AttackMode.NORMAL;
    private boolean shieldSlamExecuted = false;

    public ForceMaceModule() {
        super(Categories.Combat, "force-mace", "Auto-switches to mace when falling with shield slam detection.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        if (switchBack.get() && originalSlot != -1 && hasSwitched) {
            mc.player.getInventory().selectedSlot = originalSlot;
        }
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        // Track fall state
        if (mc.player.isOnGround()) {
            if (hasSwitched && switchBack.get() && originalSlot != -1) {
                mc.player.getInventory().selectedSlot = originalSlot;
                debugLog("Landed - restored original weapon");
            }
            reset();
            return;
        }

        // Initialize fall tracking
        if (fallStartY == -1) {
            fallStartY = mc.player.getY();
            debugLog("Started tracking fall from Y: " + fallStartY);
        }

        double currentFallDistance = fallStartY - mc.player.getY();

        // Check if we should activate
        if (currentFallDistance >= minFallDistance.get() && !hasSwitched) {
            LivingEntity target = findNearestTarget();
            if (target != null) {
                lockedTarget = target;

                // Check if target has shield up and shield slam is enabled
                if (enableShieldSlam.get() && isPlayerBlocking(target)) {
                    currentMode = AttackMode.SHIELD_SLAM;
                    debugLog("Shield user detected - using shield slam mode");
                    executeShieldSlam();
                } else {
                    currentMode = AttackMode.NORMAL;
                    debugLog("Normal target - using standard mace mode");
                    switchToMace();
                }
            }
        }

        // Handle normal auto attack
        if (currentMode == AttackMode.NORMAL && autoAttack.get() && hasSwitched && !hasAttacked && lockedTarget != null && attackCooldown == 0) {
            if (isInAttackRange(lockedTarget)) {
                mc.interactionManager.attackEntity(mc.player, lockedTarget);
                hasAttacked = true;
                attackCooldown = 20;
                debugLog("Normal mace attack executed");
            }
        }
    }

    private void executeShieldSlam() {
        if (lockedTarget == null || shieldSlamExecuted) return;

        originalSlot = mc.player.getInventory().selectedSlot;
        hasSwitched = true;
        shieldSlamExecuted = true;

        // Step 1: Switch to axe and attack
        int axeSlotIndex = axeSlot.get() - 1;
        mc.player.getInventory().selectedSlot = axeSlotIndex;

        if (isInAttackRange(lockedTarget)) {
            mc.interactionManager.attackEntity(mc.player, lockedTarget);
            debugLog("Axe hit - shield disabled");
        }

        // Step 2: Immediately switch to mace and attack
        int maceSlotIndex = maceSlot.get() - 1;
        mc.player.getInventory().selectedSlot = maceSlotIndex;

        if (isInAttackRange(lockedTarget)) {
            mc.interactionManager.attackEntity(mc.player, lockedTarget);
            debugLog("Mace hit - shield slam complete!");
        }

        hasAttacked = true;
        attackCooldown = 40; // Longer cooldown for shield slam
    }

    private void switchToMace() {
        int maceSlot = findMaceSlot();
        if (maceSlot != -1) {
            originalSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = maceSlot;
            hasSwitched = true;
            debugLog("Switched to mace for fall attack");
        }
    }

    private LivingEntity findNearestTarget() {
        double range = Math.min(targetRange.get(), getEffectiveRange());
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        Box searchBox = mc.player.getBoundingBox().expand(range);

        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, searchBox, this::isValidTarget)) {
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

    private boolean isPlayerBlocking(LivingEntity entity) {
        return entity.isBlocking() && entity.getActiveItem().getItem() instanceof ShieldItem;
    }

    private boolean isInAttackRange(LivingEntity target) {
        return mc.player.distanceTo(target) <= getEffectiveRange();
    }

    private double getEffectiveRange() {
        double baseRange = 4.0;

        // Check if any reach-extending module is active
        try {
            Module reachModule = Modules.get().get("reach");
            if (reachModule != null && reachModule.isActive()) {
                baseRange = 6.0;
            }
        } catch (Exception e) {
            // Reach module not found
        }

        // Account for hitbox expander
        try {
            Module hitboxModule = Modules.get().get("hitboxes");
            if (hitboxModule != null && hitboxModule.isActive()) {
                baseRange += 1.0;
            }
        } catch (Exception e) {
            // Hitbox module not found
        }

        return baseRange;
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

    private void debugLog(String message) {
        if (debugMessages.get()) {
            info("[ForceMace] " + message);
        }
    }

    private void reset() {
        fallStartY = -1;
        originalSlot = -1;
        hasSwitched = false;
        attackCooldown = 0;
        lockedTarget = null;
        hasAttacked = false;
        currentMode = AttackMode.NORMAL;
        shieldSlamExecuted = false;
    }

    private enum AttackMode {
        NORMAL,
        SHIELD_SLAM
    }
}