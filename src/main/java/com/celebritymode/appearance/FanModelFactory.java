package com.celebritymode.appearance;

import net.runelite.api.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

import javax.inject.*;

/**
 * Public cache access only. Decodes IDK (archive 3) and OBJ (archive 10) worn meshes. Unknown
 * opcodes fail closed, rather than rendering inventory meshes as people. Cache definitions follow
 * RuneLite's KitLoader / ItemLoader format.
 */
@Singleton
public final class FanModelFactory {
    private static final Logger log = LoggerFactory.getLogger(FanModelFactory.class);
    private static final short[] BODY_COLORS = {6798, 8741, 25238, 4626, 4550};
    private final Client client;
    private final Map<Integer, Definition> kits = new LinkedHashMap<>(), items = new HashMap<>();

    @Inject
    public FanModelFactory(Client client) {
        this.client = client;
    }

    public int chooseKit(int part, Random random, boolean defaultBob) {
        if (kits.isEmpty()) {
            int[] ids = client.getIndexConfig().getFileIds(3);
            if (ids != null)
                for (int id : ids) {
                    byte[] data = client.getIndexConfig().loadData(3, id);
                    if (data == null) continue;
                    try {
                        Definition d = decode(data, true);
                        if (!d.hidden && d.part >= 0 && d.part < 7) kits.put(id, d);
                    } catch (IllegalArgumentException ex) {
                        log.debug("Skipping unsupported identity kit {}", id);
                    }
                }
        }
        List<Integer> candidates = new ArrayList<>();
        for (Map.Entry<Integer, Definition> e : kits.entrySet())
            if (e.getValue().part == part) candidates.add(e.getKey());
        if (candidates.isEmpty()) {
            kits.clear();
            throw new IllegalStateException("Body kits not available yet: " + part);
        }
        return candidates.get(defaultBob ? 0 : random.nextInt(candidates.size()));
    }

    public Model createModel(FanAppearance appearance) {
        try {
            return assemble(appearance, true);
        } catch (RuntimeException ex) {
            if (ex instanceof CacheUnavailable) throw ex;
            log.debug(
                    "Could not assemble {} outfit, falling back to body kits", appearance.tier, ex);
            return assemble(appearance, false);
        }
    }

    private Model assemble(FanAppearance appearance, boolean equipped) {
        List<ModelData> equipment = new ArrayList<>();
        boolean[] hidden = new boolean[7];
        if (equipped)
            for (int id : appearance.equipment) {
                Definition item = items.get(id);
                if (item == null) {
                    byte[] data = client.getIndexConfig().loadData(10, id);
                    if (data == null) throw new CacheUnavailable("Item unavailable: " + id);
                    item = decode(data, false);
                    items.put(id, item);
                }
                equipment.add(mesh(item));
                // Cache wear-position tags identify covered body kits, including arms/beards.
                int[] partSlots = {11, 8, 4, 6, 9, 7, 10};
                for (int part = 0; part < partSlots.length; part++)
                    if (item.slot == partSlots[part]
                            || item.wear2 == partSlots[part]
                            || item.wear3 == partSlots[part]) hidden[part] = true;
                // A head item replaces the hair/head kit; beard coverage follows cache tags.
                if (item.slot == 0) {
                    hidden[0] = true;
                }
                if (item.slot == 4) {
                    hidden[2] = true;
                    if (item.wear2 == 6) hidden[3] = true;
                }
                if (item.slot == 7) hidden[5] = true;
                if (item.slot == 9) hidden[4] = true;
                if (item.slot == 10) hidden[6] = true;
            }
        List<ModelData> body = new ArrayList<>();
        for (int part = 0; part < 7; part++)
            if (!hidden[part]) {
                Definition kit = kits.get(appearance.kits[part]);
                if (kit == null) throw new IllegalStateException("Missing body kit");
                body.add(mesh(kit));
            }
        if (!body.isEmpty()) {
            ModelData skin = client.mergeModels(body.toArray(new ModelData[0])).cloneColors();
            recolorBody(skin.getFaceColors(), appearance.colors);
            equipment.add(skin);
        }
        return client.mergeModels(equipment.toArray(new ModelData[0]))
                .light(64, 850, -30, -50, -30);
    }

    private ModelData mesh(Definition def) {
        List<ModelData> parts = new ArrayList<>();
        for (int id : def.models)
            if (id >= 0) {
                ModelData data = client.loadModelData(id);
                if (data == null) throw new CacheUnavailable("Missing mesh " + id);
                parts.add(data);
            }
        if (parts.isEmpty()) throw new IllegalStateException("No worn model");
        ModelData data = client.mergeModels(parts.toArray(new ModelData[0])).cloneColors();
        for (short[] pair : def.recolors) data.recolor(pair[0], pair[1]);
        // Untextured meshes have a null face-texture array. RuneLite's cloneTextures()
        // clones that array directly, so only call it when textures actually exist.
        if (!def.retextures.isEmpty() && data.getFaceTextures() != null) {
            data.cloneTextures();
            for (short[] pair : def.retextures) data.retexture(pair[0], pair[1]);
        }
        if (def.offset != 0) {
            data.cloneVertices().translate(0, def.offset, 0);
        }
        return data;
    }

    static void recolorBody(short[] faces, short[] colors) {
        for (int face = 0; face < faces.length; face++)
            for (int part = 0; part < BODY_COLORS.length; part++)
                if (faces[face] == BODY_COLORS[part]) {
                    faces[face] = colors[part];
                    break;
                }
    }

    private static final class CacheUnavailable extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        CacheUnavailable(String message) {
            super(message);
        }
    }

    Definition readEquipment(int id) {
        byte[] data = client.getIndexConfig().loadData(10, id);
        return data == null ? null : decode(data, false);
    }

    int[] itemIds() {
        return client.getIndexConfig().getFileIds(10);
    }

    public void clear() {
        kits.clear();
        items.clear();
    }

    static final class Definition {
        int part = -1, slot = -1, wear2 = -1, wear3 = -1, offset;
        boolean hidden, members, tradeable;
        int noteTemplate = -1, placeholderTemplate = -1;
        String name = "";
        String[] actions = new String[5];
        int[] models = new int[] {-1, -1, -1};
        final List<short[]> recolors = new ArrayList<>(), retextures = new ArrayList<>();
    }

    static Definition decode(byte[] bytes, boolean kit) {
        try {
            ByteBuffer b = ByteBuffer.wrap(bytes);
            Definition d = new Definition();
            while (b.hasRemaining()) {
                int op = u8(b);
                if (op == 0) return d;
                if (kit) {
                    if (op == 1) d.part = u8(b);
                    else if (op == 2 || op == 5) {
                        d.models = new int[u8(b)];
                        for (int i = 0; i < d.models.length; i++)
                            d.models[i] = op == 2 ? u16(b) : b.getInt();
                    } else if (op == 3) d.hidden = true;
                    else if (op == 40 || op == 41) pairs(b, op == 40 ? d.recolors : d.retextures);
                    else if (op >= 60 && op < 70) u16(b);
                    else if (op >= 70 && op < 80) b.getInt();
                    else throw new IllegalArgumentException("Unknown kit opcode " + op);
                    continue;
                }
                if (op == 23 || op == 45) {
                    d.models[0] = op == 23 ? u16(b) : b.getInt();
                    d.offset = u8(b);
                } else if (op == 24 || op == 46) d.models[1] = op == 24 ? u16(b) : b.getInt();
                else if (op == 78 || op == 47) d.models[2] = op == 78 ? u16(b) : b.getInt();
                else if (op == 13) d.slot = u8(b);
                else if (op == 14) d.wear2 = u8(b);
                else if (op == 27) d.wear3 = u8(b);
                else if (op == 16) d.members = true;
                else if (op == 65) d.tradeable = true;
                else if (op == 98) d.noteTemplate = u16(b);
                else if (op == 149) d.placeholderTemplate = u16(b);
                else if (op == 2) d.name = string(b);
                else if (op >= 35 && op < 40) d.actions[op - 35] = string(b);
                else if (op == 40 || op == 41) pairs(b, op == 40 ? d.recolors : d.retextures);
                else if (op == 2 || op == 3 || op == 9 || (op >= 30 && op < 40)) string(b);
                else if (op == 43) {
                    u8(b);
                    while (u8(b) != 0) string(b);
                } else if (op == 12 || op == 44 || (op >= 49 && op <= 54)) b.getInt();
                else if (op == 25) {
                    u16(b);
                    u8(b);
                } else if (op == 48) {
                    b.getInt();
                    u8(b);
                } else if (op == 1
                        || (op >= 4 && op <= 8)
                        || op == 26
                        || op == 75
                        || op == 79
                        || (op >= 90 && op <= 99)
                        || (op >= 110 && op <= 112)
                        || op == 139
                        || op == 140
                        || op == 148
                        || op == 149) u16(b);
                else if (op == 27 || op == 42 || op == 113 || op == 114 || op == 115) u8(b);
                else if (op >= 100 && op < 110) b.getInt();
                else if (op == 161) {
                    int count = u16(b);
                    for (int i = 0; i < count; i++) u16(b);
                } else if (op == 249) {
                    int count = u8(b);
                    for (int i = 0; i < count; i++) {
                        int type = u8(b);
                        u8(b);
                        u8(b);
                        u8(b);
                        if (type == 1) string(b);
                        else b.getInt();
                    }
                } else if (op != 11 && op != 15 && op != 16 && op != 65 && op != 160 && op != 251)
                    throw new IllegalArgumentException("Unknown item opcode " + op);
            }
            throw new IllegalArgumentException("Unterminated definition");
        } catch (java.nio.BufferUnderflowException ex) {
            throw new IllegalArgumentException("Truncated cache definition", ex);
        }
    }

    private static int u8(ByteBuffer b) {
        return b.get() & 255;
    }

    private static int u16(ByteBuffer b) {
        return b.getShort() & 65535;
    }

    private static String string(ByteBuffer b) {
        int start = b.position();
        while (b.get() != 0) {}
        return new String(
                b.array(),
                start,
                b.position() - start - 1,
                java.nio.charset.Charset.forName("windows-1252"));
    }

    private static void pairs(ByteBuffer b, List<short[]> pairs) {
        int count = u8(b);
        for (int i = 0; i < count; i++) pairs.add(new short[] {b.getShort(), b.getShort()});
    }
}
