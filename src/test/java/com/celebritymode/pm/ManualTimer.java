package com.celebritymode.pm;

import java.util.*;
import java.util.concurrent.*;

/** Deterministic worker clock: no sleeps, real threads, or wall-clock-dependent assertions. */
final class ManualTimer extends AbstractExecutorService implements ScheduledExecutorService {
    long now;
    private boolean shutdown;
    final PriorityQueue<Task> tasks = new PriorityQueue<>();

    Runnable nextCallback() { return tasks.element().callback; }

    void runNext() {
        Task task = tasks.remove();
        now = Math.max(now, task.deadline);
        task.run();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (shutdown) throw new RejectedExecutionException();
        Task task = new Task(command, now + unit.toNanos(delay));
        tasks.add(task);
        return task;
    }

    private final class Task extends FutureTask<Void> implements ScheduledFuture<Void> {
        private final Runnable callback;
        private final long deadline;
        Task(Runnable callback, long deadline) {
            super(callback, null);
            this.callback = callback;
            this.deadline = deadline;
        }
        public long getDelay(TimeUnit unit) { return unit.convert(deadline - now, TimeUnit.NANOSECONDS); }
        public int compareTo(Delayed other) { return Long.compare(deadline, ((Task) other).deadline); }
    }

    public void shutdown() { shutdown = true; tasks.clear(); }
    public List<Runnable> shutdownNow() { shutdown(); return Collections.emptyList(); }
    public boolean isShutdown() { return shutdown; }
    public boolean isTerminated() { return shutdown; }
    public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    public void execute(Runnable runnable) { schedule(runnable, 0, TimeUnit.NANOSECONDS); }
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initial, long period, TimeUnit unit) { throw new UnsupportedOperationException(); }
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable, long initial, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
}
