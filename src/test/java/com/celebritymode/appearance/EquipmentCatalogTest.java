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
                                "Gear" + set + " " + (slot == 3 ? "bow" : "piece"),
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
