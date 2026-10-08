package com.celebritymode.generation;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class UsernameGeneratorTest {
    @Test
    public void crowdNamesAreDistinctLegalAndNeverImpersonateThePlayer() {
        Random random = new Random(41);
        Set<String> excluded = new HashSet<>(Arrays.asList("Local Hero", "Rune_River", "MORGANRS"));
        Set<String> normalized = new HashSet<>();
        for (String name : excluded) normalized.add(UsernameGenerator.normalize(name));
        for (int i = 0; i < 100; i++) {
            String name = UsernameGenerator.generate(random, excluded);
            assertTrue(name, name.matches("[A-Za-z0-9 _]{1,12}"));
            assertTrue(normalized.add(UsernameGenerator.normalize(name)));
            excluded.add(name);
        }
    }

    @Test(timeout = 1000)
    public void repeatedRandomChoicesCannotHangCollisionHandling() {
        Random repeated = new Random() {
            @Override public int nextInt(int bound) { return 0; }
        };
        Set<String> excluded = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String name = UsernameGenerator.generate(repeated, excluded);
            assertTrue(excluded.add(name));
            assertTrue(name.matches("[A-Za-z0-9 _]{1,12}"));
        }
        assertEquals("rune river", UsernameGenerator.normalize("RUNE_RIVER"));
        assertEquals("local hero", UsernameGenerator.normalize("Local\u00a0Hero"));
    }
}
