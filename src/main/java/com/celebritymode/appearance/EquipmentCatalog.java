package com.celebritymode.appearance;

import java.util.*;

import javax.inject.*;

/** Discovers current wearable cache items incrementally; no preset wardrobe or external data. */
@Singleton
public final class EquipmentCatalog {
    public static final class Item {
        public final int id, slot;
        public final String name, family;
        public final boolean members, twoHanded;

        Item(int id, FanModelFactory.Definition d) {
            this.id = id;
            slot = d.slot;
            name = d.name;
            members = d.members;
            twoHanded = d.wear2 == 5 || d.wear3 == 5;
            family = family(name);
        }
    }

    private final FanModelFactory models;
    private final Map<Integer, List<Item>> bySlot = new HashMap<>();
    private int[] ids;
    private int cursor, stride;

    @Inject
    public EquipmentCatalog(FanModelFactory models) {
        this.models = models;
    }

    public void advance(int budget) {
        if (ids == null) {
            ids = models.itemIds();
            stride = 7919;
            if (ids == null) return;
            while (ids.length > 0 && gcd(stride, ids.length) != 1) stride++;
        }
        for (int count = 0; cursor < ids.length && count < budget; count++, cursor++) {
            int id = ids[(int) ((long) cursor * stride % ids.length)];
            try {
                FanModelFactory.Definition d = models.readEquipment(id);
                if (d == null) {
                    ids = null;
                    return;
                } // Cache readiness retry without keeping an incomplete index.
                if (d.slot < 0
                        || d.models[0] < 0
                        || d.name.isEmpty()
                        || d.noteTemplate >= 0
                        || d.placeholderTemplate >= 0
                        || Arrays.stream(d.actions)
                                .noneMatch(
                                        action ->
                                                "Wear".equalsIgnoreCase(action)
                                                        || "Wield".equalsIgnoreCase(action)
                                                        || "Equip".equalsIgnoreCase(action)))
                    continue;
                String name = d.name.toLowerCase(Locale.ENGLISH);
                if (name.contains("broken")
                        || name.contains("inactive")
                        || name.contains("(l)")
                        || name.contains("(or)")
                        || name.contains("(uncharged)")
                        || name.startsWith("null")) continue;
                List<Item> pool = bySlot.computeIfAbsent(d.slot, k -> new ArrayList<>());
                pool.add(new Item(id, d));
            } catch (IllegalArgumentException ex) {
                /* Unknown future definition formats are excluded. */
            }
        }
    }

    public Item choose(int slot, FanGearTier tier, String affinity, Random random) {
        List<Item> pool = bySlot.getOrDefault(slot, Collections.emptyList());
        Item picked = null;
        int candidates = 0;
        boolean familyAvailable = false;
        if (affinity != null)
            for (Item item : pool)
                if (matches(item, tier) && item.family.equals(affinity)) {
                    familyAvailable = true;
                    break;
                }
        for (Item item : pool)
            if (matches(item, tier) && (!familyAvailable || item.family.equals(affinity)))
                if (random.nextInt(++candidates) == 0) picked = item;
        return picked;
    }

    static boolean matches(Item item, FanGearTier tier) {
        String name = item.name.toLowerCase(Locale.ENGLISH);
        switch (tier) {
            case DEFAULT_BOB:
                return false;
            case BRONZE_NOOB:
                return name.startsWith("bronze ")
                        || (item.slot == 1 && name.contains("cape"))
                        || (item.slot == 9 || item.slot == 10) && name.startsWith("leather ");
            case F2P_WARRIOR:
                return !item.members;
            case MODERN_GEAR:
                return item.id >= 20000;
            case MIDGAME_WARRIOR:
                return item.members;
            default:
                return true;
        }
    }

    static String family(String name) {
        String clean = name.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9 ]", "").trim();
        String[] words = clean.split(" +");
        return words.length > 1 && words[1].equals("moon") ? words[0] + " moon" : words[0];
    }

    public int size() {
        int size = 0;
        for (List<Item> items : bySlot.values()) size += items.size();
        return size;
    }

    public boolean complete() {
        return ids != null && cursor >= ids.length;
    }

    public void clear() {
        ids = null;
        cursor = 0;
        bySlot.clear();
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int next = a % b;
            a = b;
            b = next;
        }
        return a;
    }
}
