package com.pvpenhance.addon.modules.mace;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class WindBurstChainOptimizer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    
    private final Setting<Double> chainRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("chain-range")
        .description("Range to calculate chain possibilities.")
        .defaultValue(8.0)
        .min(4.0)
        .sliderMax(15.0)
        .build()
    );
    
    private final Setting<Integer> maxChainLength = sgGeneral.add(new IntSetting.Builder()
        .name("max-chain-length")
        .description("Maximum chain length to calculate.")
        .defaultValue(5)
        .min(2)
        .sliderMax(10)
        .build()
    );
    
    private final Setting<Boolean> showTrajectories = sgRender.add(new BoolSetting.Builder()
        .name("show-trajectories")
        .description("Show predicted bounce trajectories.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> highlightTargets = sgRender.add(new BoolSetting.Builder()
        .name("highlight-targets")
        .description("Highlight optimal chain targets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> trajectoryColor = sgRender.add(new ColorSetting.Builder()
            .name("trajectory-color")
            .description("Color of trajectory lines.")
            .defaultValue(new SettingColor(0, 255, 0, 150))
            .build()
    );

    private final Setting<SettingColor> targetColor = sgRender.add(new ColorSetting.Builder()
            .name("target-color")
            .description("Color of target highlights.")
            .defaultValue(new SettingColor(255, 255, 0, 100))
            .build()
    );

    private List<ChainTarget> chainTargets = new ArrayList<>();
    private List<Vec3d> trajectoryPoints = new ArrayList<>();
    private boolean isCalculating = false;

    public WindBurstChainOptimizer() {
        super(Categories.Combat, "wind-burst-chain-optimizer", "Visual guidance for chaining Wind Burst bounces.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        
        // Only calculate when player has mace and is falling
        if (!hasMaceEquipped() || mc.player.isOnGround()) {
            reset();
            return;
        }
        
        calculateChainPossibilities();
    }
    
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!this.isActive()) return;
        
        // Render trajectories
        if (showTrajectories.get() && !trajectoryPoints.isEmpty()) {
            renderTrajectories(event);
        }
        
        // Highlight targets
        if (highlightTargets.get() && !chainTargets.isEmpty()) {
            renderTargetHighlights(event);
        }
    }
    
    private void calculateChainPossibilities() {
        isCalculating = true;
        chainTargets.clear();
        trajectoryPoints.clear();
        
        // Find all potential targets in range
        List<LivingEntity> potentialTargets = findPotentialTargets();
        if (potentialTargets.isEmpty()) {
            isCalculating = false;
            return;
        }
        
        // Calculate chain paths
        calculateOptimalChains(potentialTargets);
        
        isCalculating = false;
    }
    
    private List<LivingEntity> findPotentialTargets() {
        List<LivingEntity> targets = new ArrayList<>();
        double range = chainRange.get();
        
        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, 
                mc.player.getBoundingBox().expand(range), this::isValidTarget)) {
            targets.add(entity);
        }
        
        return targets;
    }
    
    private void calculateOptimalChains(List<LivingEntity> targets) {
        Vec3d playerPos = mc.player.getPos();
        
        for (LivingEntity target : targets) {
            ChainTarget chainTarget = new ChainTarget(target);
            
            // Calculate damage potential based on fall distance
            double fallDistance = Math.max(0, playerPos.y - target.getY());
            chainTarget.damageScore = calculateMaceDamage(fallDistance);
            
            // Calculate chain potential (how many subsequent targets are reachable)
            chainTarget.chainPotential = calculateChainPotential(target, targets, 1);
            
            // Calculate overall score
            chainTarget.totalScore = chainTarget.damageScore + (chainTarget.chainPotential * 2);
            
            chainTargets.add(chainTarget);
        }
        
        // Sort by total score (best targets first)
        chainTargets.sort((a, b) -> Double.compare(b.totalScore, a.totalScore));
        
        // Calculate trajectory for best target
        if (!chainTargets.isEmpty()) {
            calculateTrajectory(chainTargets.get(0).entity);
        }
    }
    
    private int calculateChainPotential(LivingEntity fromTarget, List<LivingEntity> allTargets, int depth) {
        if (depth >= maxChainLength.get()) return 0;
        
        int potential = 0;
        Vec3d fromPos = fromTarget.getPos().add(0, 7, 0); // Approximate Wind Burst launch height
        
        for (LivingEntity target : allTargets) {
            if (target == fromTarget) continue;
            
            double distance = fromPos.distanceTo(target.getPos());
            if (distance <= chainRange.get()) {
                potential += 1 + calculateChainPotential(target, allTargets, depth + 1);
            }
        }
        
        return potential;
    }
    
    private double calculateMaceDamage(double fallDistance) {
        // Simplified mace damage calculation
        double baseDamage = 5.0;
        
        if (fallDistance <= 1.5) return baseDamage;
        
        double bonusDamage = 0;
        if (fallDistance <= 4.5) {
            bonusDamage = (fallDistance - 1.5) * 4;
        } else if (fallDistance <= 9.5) {
            bonusDamage = 12 + ((fallDistance - 4.5) * 2);
        } else {
            bonusDamage = 22 + (fallDistance - 9.5);
        }
        
        return baseDamage + bonusDamage;
    }
    
    private void calculateTrajectory(LivingEntity target) {
        trajectoryPoints.clear();
        
        Vec3d start = mc.player.getPos();
        Vec3d end = target.getPos();
        
        // Create a simple arc trajectory
        int steps = 20;
        for (int i = 0; i <= steps; i++) {
            double progress = (double) i / steps;
            
            double x = start.x + (end.x - start.x) * progress;
            double z = start.z + (end.z - start.z) * progress;
            
            // Create an arc
            double height = start.y + (end.y - start.y) * progress;
            height += Math.sin(progress * Math.PI) * 2; // Arc effect
            
            trajectoryPoints.add(new Vec3d(x, height, z));
        }
    }
    
    private void renderTrajectories(Render3DEvent event) {
        if (trajectoryPoints.size() < 2) return;
        
        for (int i = 0; i < trajectoryPoints.size() - 1; i++) {
            Vec3d from = trajectoryPoints.get(i);
            Vec3d to = trajectoryPoints.get(i + 1);
            
            event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z, trajectoryColor.get());
        }
    }
    
    private void renderTargetHighlights(Render3DEvent event) {
        for (int i = 0; i < Math.min(3, chainTargets.size()); i++) { // Only highlight top 3 targets
            ChainTarget target = chainTargets.get(i);
            Vec3d pos = target.entity.getPos();
            
            // Render a box around the target
            event.renderer.box(pos.x - 0.5, pos.y, pos.z - 0.5, 
                             pos.x + 0.5, pos.y + target.entity.getHeight(), pos.z + 0.5, 
                             targetColor.get(), targetColor.get(), ShapeMode.Both, 0);
        }
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
        chainTargets.clear();
        trajectoryPoints.clear();
        isCalculating = false;
    }
    
    private static class ChainTarget {
        public final LivingEntity entity;
        public double damageScore = 0;
        public int chainPotential = 0;
        public double totalScore = 0;
        
        public ChainTarget(LivingEntity entity) {
            this.entity = entity;
        }
    }
}