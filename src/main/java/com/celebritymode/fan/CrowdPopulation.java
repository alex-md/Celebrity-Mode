package com.celebritymode.fan;

import java.util.Random;

/** Slow, bounded turnover; scene loading does not restart the attendance clock. */
public final class CrowdPopulation {
    private int maximum, attendance, target, nextSurvey, nextChange;

    public int update(int tick, int configuredMaximum, int nearbyPlayers, int hour, Random random) {
        int cap = Math.max(1, Math.min(CrowdArrival.MAX_FANS, configuredMaximum));
        if (cap != maximum) {
            maximum = cap;
            attendance = target = cap;
            nextSurvey = tick + 600; // Let the entrance finish and the crowd settle first.
            nextChange = nextSurvey;
        }
        if (tick >= nextSurvey) {
            int range = Math.min(cap - 1, Math.max(1, (int) Math.ceil(cap * 0.08)));
            // Nearby real players are a local activity proxy, not a world population census.
            double activity = Math.min(1, Math.max(0, nearbyPlayers) / 40.0);
            double evening = (1 + Math.cos((hour - 20) * Math.PI / 12)) / 2;
            int missing = (int) Math.round(range *
                    (0.25 * (1 - activity) + 0.20 * (1 - evening) + 0.55 * random.nextDouble()));
            target = Math.max(1, cap - missing);
            nextSurvey = tick + 200 + random.nextInt(300);
        }
        if (tick >= nextChange) {
            attendance += Integer.signum(target - attendance);
            nextChange = tick + 60 + random.nextInt(100);
        }
        return attendance;
    }

    public void clear() {
        maximum = 0;
    }
}
