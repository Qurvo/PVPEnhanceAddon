package com.pvpenhance.addon;

import com.mojang.logging.LogUtils;
import com.pvpenhance.addon.modules.mace.*;
import com.pvpenhance.addon.modules.shield.*;
import com.pvpenhance.addon.modules.elytra.*;
import com.pvpenhance.addon.modules.general.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.pvpenhance.addon.modules.mace.PearlCatchNoMace;
import com.pvpenhance.addon.modules.elytra.WindChargePearlBoost;
import org.slf4j.Logger;

public class PvPEnhanceAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final String NAME = "PvP Enhancement Addon";

    @Override
    public void onInitialize() {
        LOG.info("Initializing " + NAME);

        // Mace PvP Modules
        Modules.get().add(new ForceMaceModule());
        Modules.get().add(new WindBurstManagement());
        Modules.get().add(new TacticalWindCharge());
        Modules.get().add(new WindBurstChainOptimizer());
        Modules.get().add(new PearlCatchNoMace());

        // Shield/SMP PvP Modules
        Modules.get().add(new ReactiveShield());
        Modules.get().add(new WeaponSwapOptimizer());
        Modules.get().add(new WTapEnhancer());
        Modules.get().add(new ShieldCounterAssist());

        // Mace Elytra PvP Modules
        Modules.get().add(new ElytraDiveAssist());
        Modules.get().add(new WindChargeFlightChain());
        Modules.get().add(new AerialTargetAcquisition());
        Modules.get().add(new CombatTransitionManager());
        Modules.get().add(new WindChargePearlBoost());

        // General Modules
        Modules.get().add(new KeybindArmorSwitch());
        Modules.get().add(new BetterAimAssist());
    }

    @Override
    public String getPackage() {
        return "com.pvpenhance.addon";
    }
}