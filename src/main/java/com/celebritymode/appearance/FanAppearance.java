package com.celebritymode.appearance;

/** Immutable spawn-time definition; no Player or PlayerComposition is ever mutated. */
public final class FanAppearance {
    public final int[] kits, equipment;
    public final short[] colors;
    public final FanGearTier tier;
    public final String weaponName;

    public FanAppearance(int[] kits, int[] equipment, short[] colors, FanGearTier tier) {
        this(kits, equipment, colors, tier, "");
    }

    public FanAppearance(
            int[] kits, int[] equipment, short[] colors, FanGearTier tier, String weaponName) {
        this.kits = kits.clone();
        this.equipment = equipment.clone();
        this.colors = colors.clone();
        this.tier = tier;
        this.weaponName = weaponName.toLowerCase(java.util.Locale.ENGLISH);
    }
}
