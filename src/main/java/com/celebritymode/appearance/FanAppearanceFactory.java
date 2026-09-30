package com.celebritymode.appearance;

import java.util.*;
import java.util.Random;

import javax.inject.*;

@Singleton
public final class FanAppearanceFactory {
    private final Random random;
    private final FanModelFactory models;
    private final EquipmentCatalog catalog;
    // Male body palettes in the cache's packed HSL color space, applied before equipment.
    private static final short[][] COLORS = {
        {6798, 8741, 25238, 4626, 4550, 5681},
        {8741, 12, 4626, 4550, 5681, 25238},
        {25238, 8741, 12, 4626, 4550, 5681},
        {4626, 12, 4550, 5681, 8741},
        {4550, 4510, 4537, 5673, 5681}
    };

    @Inject
    public FanAppearanceFactory(Random random, FanModelFactory models) {
        this.random = random;
        this.models = models;
        catalog = new EquipmentCatalog(models);
    }

    public FanAppearance createAppearance(int index, FanGearTier requested) {
        FanGearTier tier = requested == FanGearTier.MIXED ? mixedTier() : requested;
        int[] kits = new int[7];
        for (int part = 0; part < 7; part++)
            kits[part] = models.chooseKit(part, random, tier == FanGearTier.DEFAULT_BOB);
        short[] colors = new short[5];
        for (int i = 0; i < colors.length; i++)
            colors[i] = COLORS[i][random.nextInt(COLORS[i].length)];
        // Bounded scanning samples across the whole cache, so modern items appear immediately.
        catalog.advance(256);
        List<EquipmentCatalog.Item> chosen = new ArrayList<>();
        EquipmentCatalog.Item body = catalog.choose(4, tier, null, random);
        if (body != null) chosen.add(body);
        String affinity = body == null ? null : body.family;
        for (int slot : new int[] {0, 7, 10, 9, 1, 2, 3}) {
            if ((slot == 2 || slot == 0) && random.nextInt(5) == 0) continue;
            EquipmentCatalog.Item item =
                    catalog.choose(
                            slot,
                            tier,
                            tier == FanGearTier.RANDOM_FASHIONSCAPE
                                            || slot == 3
                                            || slot == 1
                                            || slot == 2
                                    ? null
                                    : affinity,
                            random);
            if (item != null) chosen.add(item);
        }
        EquipmentCatalog.Item weapon = null;
        for (EquipmentCatalog.Item item : chosen) if (item.slot == 3) weapon = item;
        if (weapon == null || !weapon.twoHanded) {
            EquipmentCatalog.Item shield = catalog.choose(5, tier, null, random);
            if (shield != null) chosen.add(shield);
        }
        int[] equipment = chosen.stream().mapToInt(item -> item.id).toArray();
        String weaponName = weapon == null ? "" : weapon.name;
        return new FanAppearance(kits, equipment, colors, tier, weaponName);
    }

    private FanGearTier mixedTier() {
        int roll = random.nextInt(100);
        return roll < 5
                ? FanGearTier.DEFAULT_BOB
                : roll < 10
                        ? FanGearTier.BRONZE_NOOB
                        : roll < 20
                                ? FanGearTier.F2P_WARRIOR
                                : roll < 40
                                        ? FanGearTier.MIDGAME_WARRIOR
                                        : roll < 65
                                                ? FanGearTier.RANDOM_FASHIONSCAPE
                                                : FanGearTier.MODERN_GEAR;
    }

    public void advanceCatalog() {
        catalog.advance(256);
    }

    public EquipmentCatalog getCatalog() {
        return catalog;
    }

    public void clear() {
        catalog.clear();
    }
}
