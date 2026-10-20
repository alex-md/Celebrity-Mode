package com.celebritymode.appearance;

import static org.junit.Assert.*;

import org.junit.Test;

public class FanModelFactoryTest {
    @Test
    public void bareHeadKeepsHairAndJaw() {
        assertBodyMeshes(null, 0, 1, 2, 3, 4, 5, 6);
    }

    @Test
    public void openHatKeepsHairAndJaw() {
        assertBodyMeshes(new byte[] {13, 0, 23, 7, (byte) 208, 0, 0},
                0, 1, 2, 3, 4, 5, 6);
    }

    @Test
    public void hairCoveringHelmetKeepsJaw() {
        assertBodyMeshes(new byte[] {13, 0, 14, 8, 23, 7, (byte) 208, 0, 0},
                1, 2, 3, 4, 5, 6);
    }

    @Test
    public void jawCoveringMaskKeepsHair() {
        assertBodyMeshes(new byte[] {13, 0, 14, 11, 23, 7, (byte) 208, 0, 0},
                0, 2, 3, 4, 5, 6);
    }

    @Test
    public void fullHelmetReplacesHairAndJaw() {
        assertBodyMeshes(new byte[] {13, 0, 14, 8, 27, 11, 23, 7, (byte) 208, 0, 0},
                2, 3, 4, 5, 6);
    }

    @Test
    public void torsoCoverageStillReplacesTorsoAndArms() {
        assertBodyMeshes(new byte[] {13, 4, 14, 6, 23, 7, (byte) 208, 0, 0},
                0, 1, 4, 5, 6);
    }

    private static void assertBodyMeshes(byte[] item, int... visibleParts) {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        FanModelFactory factory = new FanModelFactory(scene.client);
        int[] kits = new int[7];
        for (int part = 0; part < kits.length; part++)
            kits[part] = factory.chooseKit(part, new java.util.Random(0), true);
        if (item != null) scene.itemDefinitions.put(1000, item);
        factory.createModel(new FanAppearance(kits,
                item == null ? new int[0] : new int[] {1000},
                new short[] {6798, 8741, 25238, 4626, 4550}, FanGearTier.RANDOM_FASHIONSCAPE));
        java.util.List<Integer> expected = new java.util.ArrayList<>();
        if (item != null) expected.add(2000);
        // The fixture's body kit meshes are 868 + body part.
        for (int part : visibleParts) expected.add(868 + part);
        assertEquals(expected, scene.loadedModelIds);
        assertEquals(1, scene.modelBuilds);
    }

    @Test
    public void untexturedHumanMeshesSpawnWithoutCloningNullTextures() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.config.size = 30;
        assertNull(scene.faceTextures);
        scene.populate();
        assertEquals(30, scene.active.size());
        assertEquals(30, scene.modelBuilds);
        assertEquals(0, scene.textureCloneCalls);
        scene.manager.cleanup();
        assertTrue(scene.active.isEmpty());
    }

    @Test
    public void kitAndWornModelsDecode() {
        FanModelFactory.Definition kit =
                FanModelFactory.decode(
                        new byte[] {1, 2, 2, 2, 0, 10, 0, 11, 40, 1, 0, 5, 0, 6, 0}, true);
        assertEquals(2, kit.part);
        assertArrayEquals(new int[] {10, 11}, kit.models);
        assertEquals(1, kit.recolors.size());
        FanModelFactory.Definition item =
                FanModelFactory.decode(
                        new byte[] {13, 4, 14, 6, 23, 0, 42, 3, 24, 0, 43, 78, 0, 44, 0}, false);
        assertEquals(4, item.slot);
        assertEquals(6, item.wear2);
        assertArrayEquals(new int[] {42, 43, 44}, item.models);
        assertEquals(3, item.offset);
    }

    @Test
    public void colorsDoNotCascadeBetweenBodyParts() {
        short[] faces = {6798, 8741, 25238, 4626, 4550};
        FanModelFactory.recolorBody(faces, new short[] {8741, 12, 4626, 4550, 4510});
        assertArrayEquals(new short[] {8741, 12, 4626, 4550, 4510}, faces);
    }

    @Test
    public void extendedModelIdsDecode() {
        FanModelFactory.Definition kit =
                FanModelFactory.decode(new byte[] {1, 0, 5, 1, 0, 1, 0, 0, 0}, true);
        assertEquals(65536, kit.models[0]);
        FanModelFactory.Definition item =
                FanModelFactory.decode(new byte[] {45, 0, 1, 0, 0, 0, 0}, false);
        assertEquals(65536, item.models[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownOpcodesFailClosed() {
        FanModelFactory.decode(new byte[] {(byte) 199, 0}, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void truncatedDataFailsClosed() {
        FanModelFactory.decode(new byte[] {23, 0}, false);
    }
}
