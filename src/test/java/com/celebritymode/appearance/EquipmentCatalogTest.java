package com.celebritymode.appearance;

import static org.junit.Assert.*;

import com.celebritymode.TestScene;

import org.junit.Test;

import java.io.*;
import java.util.*;

public class EquipmentCatalogTest {
    private byte[] definition(
            String name, int slot, boolean members, boolean twoHanded, boolean placeholder)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(2);
        out.writeBytes(name);
        out.writeByte(0);
        out.writeByte(13);
        out.writeByte(slot);
        out.writeByte(23);
        out.writeShort(100);
        out.writeByte(0);
        out.writeByte(35);
        out.writeBytes(slot == 3 ? "Wield" : "Wear");
        out.writeByte(0);
        if (members) out.writeByte(16);
        if (twoHanded) {
            out.writeByte(14);
            out.writeByte(5);
        }
        if (placeholder) {
            out.writeByte(149);
            out.writeShort(1);
        }
        out.writeByte(0);
        return bytes.toByteArray();
    }

    @Test
    public void generatedWardrobesStayCoherentAcrossRealisticCacheFamilies() throws Exception {
        TestScene scene = new TestScene();
        String[] families = {"Rune", "Dragon", "Bandos", "Torva", "Mystic", "Ahrim's", "Ancestral",
                "Green d'hide", "Karil's", "Armadyl", "Masori"};
        int id = 30000;
        for (String family : families)
            for (int slot : new int[] {0, 4, 7, 9, 10})
                scene.itemDefinitions.put(id++, definition(family + " " + (slot == 4 ? "body" : "piece"), slot, true, false, false));
        String[] weapons = {"Bronze dagger", "Rune scimitar", "Dragon scimitar", "Abyssal whip",
                "Scythe of vitur", "Mystic staff", "Trident of the seas", "Tumeken's shadow",
                "Rune crossbow", "Armadyl crossbow", "Twisted bow"};
        for (String weapon : weapons) scene.itemDefinitions.put(id++, definition(weapon, 3, true, weapon.contains("bow") || weapon.contains("shadow") || weapon.contains("Scythe"), false));
        scene.itemDefinitions.put(id++, definition("Max cape", 1, true, false, false));
        scene.itemDefinitions.put(id++, definition("Infernal cape", 1, true, false, false));
        scene.itemDefinitions.put(id++, definition("Red cape", 1, false, false, false));
        scene.itemDefinitions.put(id++, definition("Bronze coffin", 1, true, false, false));
        scene.itemDefinitions.put(id++, definition("Rubber chicken", 3, true, false, false));
        scene.itemDefinitions.put(id++, definition("Amulet of glory", 2, true, false, false));
        scene.itemDefinitions.put(id++, definition("Dragon defender", 5, true, false, false));
        FanModelFactory models = new FanModelFactory(scene.client);
        FanAppearanceFactory factory = new FanAppearanceFactory(new Random(82), models);
        factory.advanceCatalog();
        Set<String> outfits = new HashSet<>();
        for (int i = 0; i < 800; i++) {
            FanAppearance outfit = factory.createAppearance(i, FanGearTier.RANDOM_FASHIONSCAPE);
            EquipmentCatalog.Item body = null, weapon = null;
            List<EquipmentCatalog.Item> items = new ArrayList<>();
            for (int itemId : outfit.equipment) {
                EquipmentCatalog.Item item = new EquipmentCatalog.Item(itemId, models.readEquipment(itemId));
                items.add(item);
                assertFalse(item.name.contains("coffin") || item.name.contains("chicken"));
                if (item.slot == 4) body = item;
                if (item.slot == 3) weapon = item;
            }
            assertNotNull(body);
            for (EquipmentCatalog.Item item : items) {
                if (!item.style.equals("neutral")) assertEquals(body.style, item.style);
                if (item.slot == 0 || item.slot == 7) assertEquals(body.family, item.family);
                if (item.slot == 3) assertTrue(Math.abs(item.progression - body.progression) <= 1);
                if (item.name.equals("Max cape") || item.name.equals("Infernal cape")) {
                    assertTrue(body.progression >= 4);
                    assertNotNull(weapon);
                    assertTrue(weapon.progression >= 4);
                }
            }
            outfits.add(Arrays.toString(outfit.equipment));
        }
        assertTrue("Combinations rather than a short preset wardrobe: " + outfits.size(), outfits.size() > 80);
    }

    @Test
    public void scansBoundedCacheDefinitionsAndMatchesFamilies() throws Exception {
        TestScene scene = new TestScene();
        for (int family = 0; family < 30; family++)
            for (int slot : new int[] {0, 4, 7, 3, 5}) {
                int id = 22000 + family * 10 + slot;
                scene.itemDefinitions.put(
                        id,
                        definition(
                                "Set" + family + " " + (slot == 3 ? "staff" : "piece"),
                                slot,
                                true,
                                slot == 3,
                                false));
            }
        scene.itemDefinitions.put(1117, definition("Bronze platebody", 4, false, false, false));
        scene.itemDefinitions.put(9998, definition("Broken armour", 4, true, false, false));
        scene.itemDefinitions.put(9999, definition("Placeholder armour", 4, true, false, true));
        FanModelFactory models = new FanModelFactory(scene.client);
        EquipmentCatalog catalog = new EquipmentCatalog(models);
        catalog.advance(7);
        assertTrue(catalog.size() <= 7);
        assertFalse(catalog.complete());
        for (int i = 0; i < 40; i++) catalog.advance(7);
        assertTrue(catalog.complete());
        assertEquals(151, catalog.size());
        Random random = new Random(42);
        Set<Integer> bodies = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            EquipmentCatalog.Item body = catalog.choose(4, FanGearTier.MODERN_GEAR, null, random);
            assertNotNull(body);
            assertTrue(body.id >= 20000);
            bodies.add(body.id);
            EquipmentCatalog.Item helm =
                    catalog.choose(0, FanGearTier.MODERN_GEAR, body.family, random);
            assertEquals(body.family, helm.family);
        }
        assertTrue(bodies.size() > 20);
        assertEquals(1117, catalog.choose(4, FanGearTier.BRONZE_NOOB, null, random).id);
        catalog.clear();
        assertEquals(0, catalog.size());
        assertFalse(catalog.complete());
    }

    @Test
    public void randomizedOutfitsSuppressShieldsForTwoHandedWeapons() throws Exception {
        TestScene scene = new TestScene();
        for (int set = 0; set < 50; set++)
            for (int slot : new int[] {0, 4, 7, 3, 5, 9, 10, 1})
                scene.itemDefinitions.put(
                        24000 + set * 12 + slot,
                        definition(
                                "Masori" + set + " " + (slot == 3 ? "bow" : "piece"),
                                slot,
                                true,
                                slot == 3,
                                false));
        FanModelFactory models = new FanModelFactory(scene.client);
        FanAppearanceFactory factory = new FanAppearanceFactory(new Random(3), models);
        for (int i = 0; i < 4; i++) factory.advanceCatalog();
        Set<String> outfits = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            FanAppearance appearance = factory.createAppearance(i, FanGearTier.MODERN_GEAR);
            assertTrue(appearance.equipment.length >= 5);
            assertTrue(appearance.weaponName.contains("bow"));
            for (int id : appearance.equipment) assertNotEquals(5, (id - 24000) % 12);
            outfits.add(Arrays.toString(appearance.equipment));
            assertNotNull(models.createModel(appearance));
        }
        assertTrue(outfits.size() > 40);
    }
}
