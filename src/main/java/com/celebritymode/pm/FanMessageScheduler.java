package com.celebritymode.pm;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javax.inject.*;

/** One outstanding timer and a bounded inbox. The worker never touches the RuneLite client. */
@Singleton
public final class FanMessageScheduler {
    public static final int MAX_PENDING = 32;
    public static final int MAX_PER_CLIENT_TICK = 2;
    public static final int MAX_PER_SECOND = 12;
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final long MAX_AGE = TimeUnit.SECONDS.toNanos(5);
    private final Deque<FanMessage> pending = new ArrayDeque<>();
    private final Deque<Long> dispatched = new ArrayDeque<>();
    private final Random random;
    private final LongSupplier clock;
    private final Supplier<ScheduledExecutorService> executorFactory;
    private final FanDialogueGenerator generator = new FanDialogueGenerator();
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> next;
    private TrafficIntensity intensity;
    private String playerName;
    private long session;
    private long dropped;

    @Inject
    public FanMessageScheduler() {
        this(new Random(), System::nanoTime, FanMessageScheduler::newExecutor);
    }

    FanMessageScheduler(Random random, LongSupplier clock, Supplier<ScheduledExecutorService> executorFactory) {
        this.random = random;
        this.clock = clock;
        this.executorFactory = executorFactory;
    }

    private static ScheduledExecutorService newExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "celebrity-fan-pms");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    /** Called on the client thread. Changing player names doesn't restart traffic. */
    public synchronized void configure(boolean active, TrafficIntensity intensity, String playerName) {
        if (!active) {
            if (executor != null) stop();
            return;
        }
        this.playerName = playerName;
        if (executor != null && this.intensity == intensity) return;
        stop();
        this.intensity = Objects.requireNonNull(intensity);
        this.playerName = playerName;
        executor = executorFactory.get();
        long generation = session;
        schedule(generation);
    }

    private void schedule(long generation) {
        next = executor.schedule(() -> produce(generation),
                nextDelayNanos(random, intensity), TimeUnit.NANOSECONDS);
    }

    private synchronized void produce(long generation) {
        if (generation != session || executor == null) return;
        if (pending.size() < MAX_PENDING) pending.addLast(generator.generate(random, playerName, clock.getAsLong()));
        else dropped++;
        // Relative scheduling skips missed time instead of replaying a backlog after a stalled worker.
        schedule(generation);
    }

    /** Exponential inter-arrivals form a Poisson process, naturally giving clumps and lulls. */
    static long nextDelayNanos(Random random, TrafficIntensity intensity) {
        double minutes = -Math.log1p(-random.nextDouble()) / intensity.getMessagesPerMinute();
        return Math.max(1, (long) (minutes * TimeUnit.MINUTES.toNanos(1)));
    }

    /** Bounded work even after a long client stall. Old messages are discarded rather than replayed. */
    public synchronized List<FanMessage> drain() {
        if (executor == null) return Collections.emptyList();
        long now = clock.getAsLong();
        while (!dispatched.isEmpty() && now - dispatched.peekFirst() >= SECOND) dispatched.removeFirst();
        while (!pending.isEmpty() && now - pending.peekFirst().getCreatedNanos() > MAX_AGE) {
            pending.removeFirst();
            dropped++;
        }
        List<FanMessage> batch = new ArrayList<>(MAX_PER_CLIENT_TICK);
        while (!pending.isEmpty() && batch.size() < MAX_PER_CLIENT_TICK && dispatched.size() < MAX_PER_SECOND) {
            batch.add(pending.removeFirst());
            dispatched.addLast(now);
        }
        return batch;
    }

    public synchronized int getPendingCount() { return pending.size(); }
    public synchronized long getDroppedCount() { return dropped; }

    public synchronized void shutdown() { stop(); }

    private void stop() {
        session++;
        if (next != null) next.cancel(false);
        if (executor != null) executor.shutdownNow();
        next = null;
        executor = null;
        intensity = null;
        pending.clear();
        dispatched.clear();
        generator.clear();
        dropped = 0;
    }
}
