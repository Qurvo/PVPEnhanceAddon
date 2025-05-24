This Addon is for Meteor 1.21.4 ONLY. Will add support for other versions in the future.
This is the first addon I have made so bear with me if features dont work exactly how you want and Im open to suggestions.
Let me know any features you guys would like to see.
16 Modules focused on Shield PVP and Mace PVP, mess around with the modules to find what works for you.
Each Module and how to use:
General Modules
1. BetterAimAssist
Purpose: Advanced aim assist with sticky targeting for combat
How to use:

Enable when you want smooth aim assistance in combat
Adjust target range (2-10 blocks) for detection distance
Set unlock distance (higher than target range) to stop tracking distant enemies
Configure aim speed and smoothness for natural-looking movement
Enable prediction to lead moving targets
The module automatically locks onto the closest/lowest health enemy and smoothly aims at them

2. KeybindArmorSwitch
Purpose: Quick hotbar armor switching with right-click
How to use:

Set your armor slot (1-9) where you keep elytra/chestplate
Bind this module to a key
When activated, it automatically switches to that slot, right-clicks to equip, then returns to your original slot
Module auto-disables after one use

Shield Combat Modules
3. WTapEnhancer
Purpose: Automates W-tapping for better knockback in fights
How to use:

Enable before engaging in melee combat
Set tap duration (how long to release W key) - shorter for subtle, longer for obvious
Configure tap delay for timing before the W-tap starts
Enable "only on hit" to only W-tap when you successfully hit someone
Add randomization to make it less detectable
Module automatically releases W key briefly after each attack for maximum knockback

4. WeaponSwapOptimizer
Purpose: Performs attribute swap (sword→axe→attack→sword) when opponent raises shield
How to use:

Set your sword and axe slots in hotbar
Enable when fighting shield users
Module detects when enemies are blocking and performs the rapid weapon swap combo
"Only when targeting" makes it only activate when looking at the shield user
Automatically exploits the attribute swap timing for maximum damage

5. ShieldCounterAssist
Purpose: Semi-automated counter-attacks after successful blocks
How to use:

Enable when using a shield in combat
Set counter delay (ticks to wait after blocking before attacking)
Configure counter window (how long you have to counter-attack)
Enable auto-lower shield to automatically drop shield for counter-attacks
Module detects when you stop blocking and automatically attacks nearby enemies

6. ReactiveShield
Purpose: Automatically blocks threats for short duration then releases
How to use:

Enable when you have a shield equipped
Configure what to block (projectiles and/or melee attacks)
Set reaction time to simulate human response
Adjust detection ranges for different threat types
Module automatically raises shield when detecting incoming attacks, holds briefly, then lowers

Mace Combat Modules
7. WindBurstManagement
Purpose: Optimizes Wind Burst enchantment timing and chaining
How to use:

Enable when using a mace with Wind Burst enchantment
Set minimum fall distance before optimization kicks in
Enable chain bounces to automatically chain multiple Wind Burst attacks
Configure max chain length to limit bouncing
Module tracks your falls and optimizes Wind Burst timing for maximum effectiveness

8. WindBurstChainOptimizer
Purpose: Visual guidance for chaining Wind Burst bounces
How to use:

Enable when planning Wind Burst chains with a mace
Set chain range for calculating possibilities
Configure max chain length for planning
Enable visual features to see trajectory lines and target highlights
Module shows you the best targets for chaining Wind Burst attacks with visual overlays

9. PearlCatchNoMace
Purpose: Throws pearl at crosshair target, then disables
How to use:

Set your pearl slot (1-9)
Aim at your target or have them in your crosshair
Activate the module (bind to a key)
Module automatically switches to pearls, aims with prediction, throws, then switches back
Auto-disables after one use

10. ForceMaceModule
Purpose: Auto-switches to mace when falling with shield slam detection
How to use:

Set minimum fall distance to trigger the switch
Configure your mace and axe slots
Enable shield slam detection for advanced combo against shield users
When falling near enemies, it switches to mace automatically
If enemy has shield up, performs axe→mace combo instead
Enable switch back to return to original weapon after landing

Elytra/Aerial Combat Modules
11. TacticalWindCharge
Purpose: Handles weapon and equipment transitions during aerial combat
How to use:

Enable during elytra combat situations
Set transition range for detecting combat
Enable auto armor switch to change between elytra and chestplate based on situation
Configure smart weapon switching to use optimal weapons (mace for height advantage, axe for shields)
Module automatically manages your equipment based on combat state

12. WindChargePearlBoost
Purpose: Throws pearl then wind charge in same direction for guaranteed hit
How to use:

Set pearl and wind charge slots
Configure delay between throwing pearl and wind charge
Aim where you want to teleport and activate
Module throws pearl, waits specified delay, then throws wind charge in exact same direction
Creates a boosted pearl throw for longer range

13. WindChargeFlightChain
Purpose: Optimizes wind charge usage for maintaining aerial superiority
How to use:

Enable when flying with elytra and carrying wind charges
Set activation height threshold (when to use wind charges)
Configure chain cooldown between uses
Enable auto firework as backup mobility
Module automatically uses wind charges when losing altitude or below threshold height

14. ElytraDiveAssist
Purpose: Auto-equips chestplate and switches to mace when diving near targets
How to use:

Set target range and minimum fall distance
Configure chestplate slot in hotbar
Enable when planning dive attacks
Module detects when you're falling near enemies and automatically equips combat gear
Switches from elytra to chestplate and selects mace for dive bombing

15. CombatTransitionManager
Purpose: Handles weapon and equipment transitions during aerial combat (similar to TacticalWindCharge)
How to use:

Duplicate functionality to TacticalWindCharge
Manages transitions between aerial movement, aerial combat, ground combat, and neutral states
Automatically switches equipment and weapons based on combat situation

16. AerialTargetAcquisition
Purpose: Helps identify and prioritize targets during aerial combat
How to use:

Enable when flying and looking for targets
Set acquisition range for target detection
Configure target prioritization (grounded, isolated, avoid shielded)
Enable visual highlights to see prioritized targets
Module analyzes all nearby enemies and highlights the best targets for aerial attacks
Shows score-based targeting with visual indicators

General Usage Tips:

Many modules auto-disable after use (pearl throwers, armor switchers)
Most have configurable slots - make sure to set these correctly
Visual modules show helpful overlays for planning attacks
Combat modules work together - you can enable multiple for combined effects
Adjust timing and range settings based on your playstyle and server lag
