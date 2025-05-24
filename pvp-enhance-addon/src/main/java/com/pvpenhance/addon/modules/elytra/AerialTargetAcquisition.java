package com.pvpenhance.addon.modules.elytra;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.ColorSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class AerialTargetAcquisition extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> acquisitionRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("acquisition-range")
            .description("Range to search for targets during flight.")
            .defaultValue(20.0)
            .min(8.0)
            .sliderMax(40.0)
            .build()
    );

    private final Setting<Boolean> prioritizeGrounded = sgGeneral.add(new BoolSetting.Builder()
            .name("prioritize-grounded")
            .description("Prioritize targets on the ground.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> prioritizeIsolated = sgGeneral.add(new BoolSetting.Builder()
            .name("prioritize-isolated")
            .description("Prioritize isolated targets over groups.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> avoidShielded = sgGeneral.add(new BoolSetting.Builder()
            .name("avoid-shielded")
            .description("Avoid targets that are actively blocking.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> highlightTargets = sgRender.add(new BoolSetting.Builder()
            .name("highlight-targets")
            .description("Highlight prioritized targets.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> renderOpacity = sgRender.add(new IntSetting.Builder()
            .name("render-opacity")
            .description("Opacity of target highlights (0-255).")
            .defaultValue(150)
            .min(0)
            .sliderMax(255)
            .build()
    );

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> primaryTargetColor = sgRender.add(new ColorSetting.Builder()
            .name("primary-target-color")
            .description("Color for the primary target.")
            .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 0, 0, 150))
            .build()
    );

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> secondaryTargetColor = sgRender.add(new ColorSetting.Builder()
            .name("secondary-target-color")
            .description("Color for secondary targets.")
            .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 165, 0, 100))
            .build()
    );

    private List<PrioritizedTarget> prioritizedTargets = new ArrayList<>();
    private LivingEntity primaryTarget;

    public AerialTargetAcquisition() {
        super(Categories.Combat, "aerial-target-acquisition", "Helps identify and prioritize targets during aerial combat.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Only actively acquire targets when flying
        if (!mc.player.isGliding() && !isAerialPosition()) {
            reset();
            return;
        }

        updateTargetPriorities();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!this.isActive() || !highlightTargets.get()) return;

        renderTargetHighlights(event);
    }

    private void updateTargetPriorities() {
        prioritizedTargets.clear();

        List<LivingEntity> potentialTargets = findPotentialTargets();

        for (LivingEntity entity : potentialTargets) {
            if (!isValidTarget(entity)) continue;

            PrioritizedTarget target = new PrioritizedTarget(entity);
            calculateTargetScore(target);
            prioritizedTargets.add(target);
        }

        // Sort by score (highest first)
        prioritizedTargets.sort((a, b) -> Double.compare(b.score, a.score));

        // Set primary target
        primaryTarget = prioritizedTargets.isEmpty() ? null : prioritizedTargets.get(0).entity;
    }

    private List<LivingEntity> findPotentialTargets() {
        double range = acquisitionRange.get();
        List<LivingEntity> targets = new ArrayList<>();

        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class,
                mc.player.getBoundingBox().expand(range), this::isValidTarget)) {
            targets.add(entity);
        }

        return targets;
    }

    private void calculateTargetScore(PrioritizedTarget target) {
        LivingEntity entity = target.entity;
        double score = 100.0; // Base score

        // Distance factor (closer is better, but not too close for aerial attacks)
        double distance = mc.player.distanceTo(entity);
        if (distance < 5.0) {
            score -= 20; // Too close for aerial attack
        } else if (distance < 15.0) {
            score += 30; // Optimal range
        } else {
            score -= (distance - 15.0) * 2; // Penalty for being far
        }

        // Height advantage factor
        double heightDiff = mc.player.getY() - entity.getY();
        if (heightDiff > 3.0) {
            score += heightDiff * 5; // Bonus for height advantage
        } else {
            score -= 15; // Penalty for no height advantage
        }

        // Grounded target bonus
        if (prioritizeGrounded.get() && entity.isOnGround()) {
            score += 25;
        }

        // Shield penalty
        if (avoidShielded.get() && isEntityBlocking(entity)) {
            score -= 40;
        }

        // Isolation bonus
        if (prioritizeIsolated.get()) {
            int nearbyEnemies = countNearbyEnemies(entity, 8.0);
            if (nearbyEnemies == 0) {
                score += 20; // Isolated target bonus
            } else {
                score -= nearbyEnemies * 5; // Penalty for grouped enemies
            }
        }

        // Health factor (lower health targets are easier)
        float healthPercentage = entity.getHealth() / entity.getMaxHealth();
        score += (1.0 - healthPercentage) * 15;

        // Line of sight bonus
        if (mc.player.canSee(entity)) {
            score += 10;
        }

        target.score = Math.max(0, score);
    }

    private int countNearbyEnemies(LivingEntity center, double range) {
        int count = 0;

        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class,
                center.getBoundingBox().expand(range), this::isValidTarget)) {

            if (entity != center) {
                count++;
            }
        }

        return count;
    }

    private void renderTargetHighlights(Render3DEvent event) {
        int opacity = renderOpacity.get();

        for (int i = 0; i < Math.min(5, prioritizedTargets.size()); i++) {
            PrioritizedTarget target = prioritizedTargets.get(i);
            Vec3d pos = target.entity.getPos();

            // Create colors with custom opacity
            Color color;
            if (i == 0) {
                SettingColor baseColor = primaryTargetColor.get();
                color = new Color(baseColor.r, baseColor.g, baseColor.b, opacity);
            } else {
                SettingColor baseColor = secondaryTargetColor.get();
                color = new Color(baseColor.r, baseColor.g, baseColor.b, opacity);
            }

            // Render target box
            event.renderer.box(pos.x - 0.6, pos.y, pos.z - 0.6,
                    pos.x + 0.6, pos.y + target.entity.getHeight(), pos.z + 0.6,
                    color, color, ShapeMode.Both, 0);

            // Render score text above target
            String scoreText = String.format("%.0f", target.score);
        }

        // Draw line to primary target
        if (primaryTarget != null) {
            Vec3d playerPos = mc.player.getEyePos();
            Vec3d targetPos = primaryTarget.getPos().add(0, primaryTarget.getHeight() / 2, 0);

            SettingColor baseColor = primaryTargetColor.get();
            Color lineColor = new Color(baseColor.r, baseColor.g, baseColor.b, opacity);

            event.renderer.line(playerPos.x, playerPos.y, playerPos.z,
                    targetPos.x, targetPos.y, targetPos.z,
                    lineColor);
        }
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        return entity.isAlive() && !entity.isRemoved();
    }

    private boolean isEntityBlocking(LivingEntity entity) {
        return entity.isBlocking();
    }

    private boolean isAerialPosition() {
        // Consider player in aerial position if above ground level
        return mc.player.getY() > mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                (int) mc.player.getX(), (int) mc.player.getZ()) + 5;
    }

    public LivingEntity getPrimaryTarget() {
        return primaryTarget;
    }

    public List<PrioritizedTarget> getPrioritizedTargets() {
        return new ArrayList<>(prioritizedTargets);
    }

    private void reset() {
        prioritizedTargets.clear();
        primaryTarget = null;
    }

    public static class PrioritizedTarget {
        public final LivingEntity entity;
        public double score;

        public PrioritizedTarget(LivingEntity entity) {
            this.entity = entity;
            this.score = 0;
        }
    }
}