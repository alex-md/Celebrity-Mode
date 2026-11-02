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
        public final int progression;
        public final String style;

        Item(int id, FanModelFactory.Definition d) {
            this.id = id;
            slot = d.slot;
            name = d.name;
            members = d.members;
            twoHanded = d.wear2 == 5 || d.wear3 == 5;
            family = family(name);
            progression = progression(name);
            style = style(name);
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
                        || name.contains("damaged")
                        || name.contains("(0)")
                        || name.contains("(l)")
                        || name.contains("(or)")
                        || name.contains("(uncharged)")
                        || name.startsWith("null")
                        || novelty(name)
                        || (d.slot == 1 && !name.contains("cape") && !name.contains("cloak") && !name.startsWith("ava's "))) continue;
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

    /** One account's progression and combat style, with family preference inside the compatible pool. */
    public Item chooseCompatible(int slot, FanGearTier tier, Item body, Item weapon, Random random) {
        List<Item> pool = bySlot.getOrDefault(slot, Collections.emptyList());
        Item picked = null;
        boolean matchingArmour = body != null && (slot == 0 || slot == 7)
                && pool.stream().anyMatch(item -> matches(item, tier) && item.family.equals(body.family));
        double best = -1;
        for (Item item : pool) {
            if (!matches(item, tier) || (matchingArmour && !item.family.equals(body.family))) continue;
            if (body != null) {
                if (!item.style.equals("neutral") && !item.style.equals(body.style)) continue;
                if (item.progression > body.progression + 1) continue;
                if ((slot == 3 || slot == 7 || slot == 0) && item.progression < body.progression - 1) continue;
                if (prestige(item.name) && (body.progression < 4 || weapon == null || weapon.progression < 4)) continue;
            } else if (prestige(item.name)) continue;
            double weight = body != null && item.family.equals(body.family) ? 8 : 1;
            double score = Math.pow(random.nextDouble(), 1.0 / weight);
            if (score > best) { best = score; picked = item; }
        }
        return picked;
    }

    static boolean matches(Item item, FanGearTier tier) {
        switch (tier) {
            case DEFAULT_BOB: return false;
            case BRONZE_NOOB:
                return item.name.toLowerCase(Locale.ENGLISH).startsWith("bronze ")
                        || (item.style.equals("neutral") && item.progression == 0)
                        || ((item.slot == 9 || item.slot == 10) && item.name.toLowerCase(Locale.ENGLISH).startsWith("leather "));
            case F2P_WARRIOR: return !item.members && item.progression <= 2;
            case MODERN_GEAR: return item.progression >= 3 || (item.progression == 2 && item.id >= 20000);
            case MIDGAME_WARRIOR: return item.progression >= 1 && item.progression <= 4;
            default: return true;
        }
    }

    private static boolean novelty(String name) {
        return name.matches(".*(coffin|chicken|rubber|easter|halloween|santa|reindeer|snowman|jester|clown|marionette|basket|sled|cabbage|banana|spook|pumpkin|bunny|frog|flipper|flared|lederhosen|zombie|skeleton|mime|camouflage|goblin|propeller|gnome scarf|giant boulder|sack of presents).*");
    }

    private static boolean prestige(String name) {
        String n = name.toLowerCase(Locale.ENGLISH);
        return n.contains("max cape") || n.contains("infernal") || n.contains("dizana");
    }

    static int progression(String name) {
        String n = name.toLowerCase(Locale.ENGLISH);
        if (n.matches(".*(torva|masori|ancestral|inquisitor|justiciar|virtus|crystal|twisted bow|tumeken|scythe of|soulreaper|max cape|infernal|dizana|zaryte|ultor|bellator|venator|magus).*")) return 5;
        if (n.matches(".*(bandos|armadyl|ahrim|karil|guthan|torag|dharok|verac|bludgeon|abyssal|bowfa|bow of faerdhinen|fang|trident|toxic|sanguinesti|avernic|primordial|pegasian|eternal|zenyte|torture|anguish|tormented|occult|blood moon|blue moon|eclipse moon|barrows gloves|fire cape).*")) return 4;
        if (n.matches(".*(dragon|granite|obsidian|mystic|ahrim|ava's|blessed|god d.hide|saradomin|guthix|zamorak|ancient|dark bow|fighter torso|neitiznot|berserker|archer|seers).*")) return 3;
        if (n.matches(".*(rune|green d.hide|blue d.hide|red d.hide|black d.hide|splitbark|imbued|glory|combat bracelet).*")) return 2;
        if (n.matches(".*(mithril|adamant|black |studded|snakeskin|xerician|wizard|battlestaff|power|strength|magic amulet).*")) return 1;
        if (n.matches(".*(bronze|iron |steel|leather|shortbow|longbow|oak |willow |staff|wooden|cape|cloak|boots|gloves|amulet).*")) return 0;
        return 2; // Unclassified future armour remains usable without treating its ID as wealth.
    }

    static String style(String name) {
        String n = name.toLowerCase(Locale.ENGLISH);
        if (n.matches(".*(masori|armadyl|karil|crystal|d.hide|dragonhide|leather|studded|snakeskin|bow|crossbow|blowpipe|javelin|dart|throwing|anguish|pegasian|ranger|archer|eclipse moon|avas|ava's|dizana).*")) return "ranged";
        if (n.matches(".*(ancestral|virtus|ahrim|mystic|wizard|xerician|splitbark|swampbark|bloodbark|dagon.hai|infinity|robe|staff|wand|trident|tumeken|sanguinesti|tome|book|arcane|spectral|occult|tormented|eternal|blue moon|mage).*")) return "magic";
        if (n.matches(".*(bronze|iron |steel|black |mithril|adamant|rune |dragon |torva|bandos|inquisitor|justiciar|guthan|torag|dharok|verac|granite|obsidian|plate|chain|sword|scimitar|dagger|mace|halberd|spear|whip|defender|shield|maul|bludgeon|fang|scythe|primordial|torture|berserker|fighter|neitiznot|blood moon).*")) return "melee";
        return "neutral";
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
