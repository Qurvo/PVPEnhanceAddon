package com.pvpenhance.addon.modules.elytra;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;

public class CombatTransitionManager extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWeapons = settings.createGroup("Weapons");
    
    private final Setting<Double> transitionRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("transition-range")
        .description("Range to detect combat situations.")
        .defaultValue(8.0)
        .min(3.0)
        .sliderMax(15.0)
        .build()
    );
    
    private final Setting<Integer> transitionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("transition-delay")
        .description("Delay between transitions in ticks.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build()
    );
    
    private final Setting<Boolean> autoArmorSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-armor-switch")
        .description("Automatically switch between elytra and chestplate.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> smartWeaponSwitch = sgWeapons.add(new BoolSetting.Builder()
        .name("smart-weapon-switch")
        .description("Automatically switch to optimal weapon for situation.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> prioritizeMace = sgWeapons.add(new BoolSetting.Builder()
        .name("prioritize-mace")
        .description("Prioritize mace when height advantage exists.")
        .defaultValue(true)
        .visible(smartWeaponSwitch::get)
        .build()
    );
    
    private final Setting<Double> maceHeightThreshold = sgWeapons.add(new DoubleSetting.Builder()
        .name("mace-height-threshold")
        .description("Height advantage needed to prefer mace.")
        .defaultValue(4.0)
        .min(2.0)
        .sliderMax(10.0)
        .visible(() -> smartWeaponSwitch.get() && prioritizeMace.get())
        .build()
    );

    private CombatState currentState = CombatState.NEUTRAL;
    private int transitionCooldown = 0;
    private LivingEntity currentTarget;
    private boolean hasElytraEquipped = false;

    public CombatTransitionManager() {
        super(Categories.Combat, "combat-transition-manager", "Handles weapon and equipment transitions during aerial combat.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        
        if (transitionCooldown > 0) {
            transitionCooldown--;
        }
        
        // Update current state
        updateCombatState();
        
        // Handle state-based transitions
        handleStateTransitions();
    }
    
    private void updateCombatState() {
        ItemStack chestItem = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
        hasElytraEquipped = chestItem.getItem().toString().toLowerCase().contains("elytra");
        boolean isFlying = mc.player.isGliding();
        boolean inCombat = hasNearbyEnemies();
        
        CombatState newState = determineOptimalState(isFlying, inCombat);
        
        if (newState != currentState) {
            currentState = newState;
            info("Combat state changed to: " + currentState);
        }
    }
    
    private CombatState determineOptimalState(boolean isFlying, boolean inCombat) {
        if (isFlying) {
            return inCombat ? CombatState.AERIAL_COMBAT : CombatState.AERIAL_MOVEMENT;
        } else if (inCombat) {
            return CombatState.GROUND_COMBAT;
        } else {
            return CombatState.NEUTRAL;
        }
    }
    
    private void handleStateTransitions() {
        if (transitionCooldown > 0) return;
        
        switch (currentState) {
            case AERIAL_COMBAT:
                handleAerialCombat();
                break;
                
            case GROUND_COMBAT:
                handleGroundCombat();
                break;
                
            case AERIAL_MOVEMENT:
                handleAerialMovement();
                break;
                
            case NEUTRAL:
                handleNeutralState();
                break;
        }
    }
    
    private void handleAerialCombat() {
        // Ensure we have combat-ready equipment
        if (autoArmorSwitch.get() && hasElytraEquipped) {
            // Keep elytra for mobility, but prepare weapons
            if (smartWeaponSwitch.get()) {
                switchToOptimalWeapon();
            }
        }
    }
    
    private void handleGroundCombat() {
        // Switch to maximum protection
        if (autoArmorSwitch.get() && hasElytraEquipped) {
            switchToChestplate();
        }
        
        if (smartWeaponSwitch.get()) {
            switchToOptimalWeapon();
        }
    }
    
    private void handleAerialMovement() {
        // Ensure elytra is equipped for mobility
        if (autoArmorSwitch.get() && !hasElytraEquipped) {
            switchToElytra();
        }
    }
    
    private void handleNeutralState() {
        // Default to protective equipment when not in specific situations
        if (autoArmorSwitch.get() && hasElytraEquipped && !mc.player.isGliding()) {
            switchToChestplate();
        }
    }
    
    private void switchToOptimalWeapon() {
        LivingEntity target = findNearestTarget();
        if (target == null) return;
        
        WeaponType optimalWeapon = determineOptimalWeapon(target);
        int weaponSlot = findWeaponSlot(optimalWeapon);
        
        if (weaponSlot != -1 && weaponSlot != mc.player.getInventory().selectedSlot) {
            mc.player.getInventory().selectedSlot = weaponSlot;
            transitionCooldown = transitionDelay.get();
            info("Switched to " + optimalWeapon);
        }
    }
    
    private WeaponType determineOptimalWeapon(LivingEntity target) {
        double heightDiff = mc.player.getY() - target.getY();
        
        // Prioritize mace if we have height advantage
        if (prioritizeMace.get() && heightDiff > maceHeightThreshold.get()) {
            return WeaponType.MACE;
        }
        
        // Use axe if target is blocking
        if (target.isBlocking()) {
            return WeaponType.AXE;
        }
        
        // Default to sword for general combat
        return WeaponType.SWORD;
    }
    
    private int findWeaponSlot(WeaponType weaponType) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            switch (weaponType) {
                case MACE:
                    // Check for mace by name since Items.MACE doesn't exist in this version
                    if (stack.getItem().toString().toLowerCase().contains("mace")) return i;
                    break;
                case AXE:
                    if (stack.getItem() instanceof AxeItem) return i;
                    break;
                case SWORD:
                    if (stack.getItem() instanceof SwordItem) return i;
                    break;
            }
        }
        return -1;
    }
    
    private void switchToChestplate() {
        int chestplateSlot = findChestplateSlot();
        if (chestplateSlot != -1) {
            swapChestArmor(chestplateSlot);
            transitionCooldown = transitionDelay.get() * 2; // Longer cooldown for armor swaps
            info("Switched to chestplate");
        }
    }

    private int findElytraSlot() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem().toString().toLowerCase().contains("elytra")) {
                return i;
            }
        }
        return -1;
    }

    private void switchToElytra() {
        int elytraSlot = findElytraSlot();
        if (elytraSlot != -1) {
            swapChestArmor(elytraSlot);
            transitionCooldown = transitionDelay.get() * 2;
            info("Switched to elytra");
        }
    }
    
    private void swapChestArmor(int slot) {
        // Perform inventory click to swap armor
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 
            slot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 
            6, 0, SlotActionType.PICKUP, mc.player); // Chest slot is 6
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 
            slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private int findChestplateSlot() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof ArmorItem) {
                // Check by item name if it's a chestplate
                String itemName = stack.getItem().toString().toLowerCase();
                if (itemName.contains("chestplate")) {
                    return i;
                }
            }
        }
        return -1;
    }

    
    private boolean hasNearbyEnemies() {
        currentTarget = findNearestTarget();
        return currentTarget != null;
    }
    
    private LivingEntity findNearestTarget() {
        double range = transitionRange.get();
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
    
    private void reset() {
        currentState = CombatState.NEUTRAL;
        transitionCooldown = 0;
        currentTarget = null;
        hasElytraEquipped = false;
    }
    
    private enum CombatState {
        NEUTRAL,
        AERIAL_MOVEMENT,
        AERIAL_COMBAT,
        GROUND_COMBAT
    }
    
    private enum WeaponType {
        SWORD,
        AXE,
        MACE
    }
}