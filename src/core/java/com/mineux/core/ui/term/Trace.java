package com.mineux.core.ui.term;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What each module's steps are doing, for its debug panel ({@link ReadoutPane}) and its
 * confirmations ({@link Notify}): the step it is on, how far through that step's wait it is,
 * the delay each step last used, and a short history of them to graph.
 * <p>
 * Two kinds of step, from the module's side:
 * <ul>
 *   <li><b>Timed</b> -- the macros' jittered waits. {@link #arm} when the delay is drawn,
 *       {@link #fire} when the action goes out; the panel fills a bar from one to the other
 *       and keeps the delay that was drawn, and how long it really took.</li>
 *   <li><b>Gauge</b> -- a condition filling up rather than a clock running down: a weapon's
 *       charge, a fall, an aim closing in. {@link #gauge} sets how far along it is each tick,
 *       {@link #sample} records a value when it lands.</li>
 * </ul>
 * Recorded whether or not a panel is open, so opening one shows history rather than
 * nothing; it is a few field writes per step. Every module calls it on the client thread;
 * the methods are synchronized anyway, as the knockback path starts on the network one.
 */
public final class Trace {

    /** How many past values each step keeps for its graph. */
    static final int HISTORY = 24;
    /** How long a step stays lit after it fires, or red after it is skipped. */
    static final long FLASH_MS = 350;

    static final class Step {
        final String name;
        /** A clock step ({@link #arm}/{@link #fire}) rather than a gauge. */
        boolean timed = true;
        boolean waiting;
        long armedAt;
        long delay;
        /** A gauge's live progress, 0 to 1, or -1 when it is not showing one. */
        float gauge = -1f;
        String gaugeText = "";
        /** The delay drawn for the last completed wait, and how long it really took. */
        long lastDelay = -1;
        long lastActual = -1;
        /** The step's last result, as the panel prints it. */
        String lastText = "";
        long firedAt;
        long skippedAt;
        String skipWhy = "";
        final float[] history = new float[HISTORY];
        int count;
        int head;

        Step(String name) {
            this.name = name;
        }

        void push(float value) {
            history[head] = value;
            head = (head + 1) % HISTORY;
            if (count < HISTORY) count++;
        }

        /** The {@code i}th value of the graph, oldest first. */
        float sample(int i) {
            return history[Math.floorMod(head - count + i, HISTORY)];
        }

        /** How full its bar is now: the wait's share gone, the gauge, or full just after it fired. */
        float progress(long now) {
            if (waiting) return delay <= 0 ? 1f : clamp((now - armedAt) / (float) delay);
            if (gauge >= 0f) return gauge;
            return now - firedAt < FLASH_MS ? 1f : 0f;
        }

        boolean active(long now) {
            return waiting || gauge >= 0f || now - firedAt < FLASH_MS;
        }
    }

    static final class Run {
        final String id;
        final List<Step> steps = new ArrayList<>();
        /** When anything last happened, for the idle/live light. */
        long touchedAt;
        /** One line of what the module as a whole is doing, or empty. */
        String state = "";

        Run(String id) {
            this.id = id;
        }

        Step find(String name) {
            for (Step s : steps) if (s.name.equals(name)) return s;
            return null;
        }

        Step step(String name) {
            Step s = find(name);
            if (s == null) {
                s = new Step(name);
                steps.add(s);
            }
            return s;
        }

        /**
         * The sequence as one number, 0 to 1: every step before the one in progress counts
         * as done, that one as far as its own bar is, and nothing after it. 0 when idle.
         */
        float progress(long now) {
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                if (s.waiting || s.gauge >= 0f) return (i + s.progress(now)) / steps.size();
            }
            return 0f;
        }

        boolean busy(long now) {
            for (Step s : steps) if (s.waiting || s.gauge >= 0f) return true;
            return false;
        }
    }

    private static final Map<String, Run> RUNS = new HashMap<>();

    private Trace() {}

    static synchronized Run get(String id) {
        return RUNS.get(id);
    }

    private static synchronized Run run(String id) {
        Run r = RUNS.get(id);
        if (r == null) {
            r = new Run(id);
            RUNS.put(id, r);
        }
        return r;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    // ---- what the modules call ------------------------------------------

    /** The module's steps, in the order they run; a step named later that is not here is added at the end. */
    public static synchronized void steps(String id, String... names) {
        Run r = run(id);
        for (String n : names) r.step(n);
    }

    /** A timed step starts its wait of {@code delayMs}. */
    public static synchronized void arm(String id, String step, long delayMs) {
        long now = System.currentTimeMillis();
        Run r = run(id);
        Step s = r.step(step);
        s.timed = true;
        s.gauge = -1f;
        s.waiting = true;
        s.armedAt = now;
        s.delay = Math.max(0L, delayMs);
        r.touchedAt = now;
    }

    /**
     * The step's action went out: its wait is over and becomes a point on its graph. A step
     * that was never armed (a clicker's first click) only lights up; a made-up zero on the
     * graph would read as a delay nobody drew.
     */
    public static synchronized void fire(String id, String step) {
        note(id, "fire " + step);
        long now = System.currentTimeMillis();
        Run r = run(id);
        Step s = r.step(step);
        r.touchedAt = now;
        if (!s.waiting) {
            s.gauge = -1f;
            s.firedAt = now;
            return;
        }
        long actual = now - s.armedAt;
        long planned = s.delay;
        s.waiting = false;
        s.gauge = -1f;
        s.lastDelay = planned;
        s.lastActual = actual;
        s.lastText = planned + "ms";
        s.push(planned);
        s.firedAt = now;
        r.touchedAt = now;
    }

    /** A gauge step's live progress, 0 to 1, and what to print beside it. */
    public static synchronized void gauge(String id, String step, float progress, String text) {
        Run r = run(id);
        Step s = r.step(step);
        s.timed = false;
        s.waiting = false;
        s.gauge = clamp(progress);
        s.gaugeText = text;
        r.touchedAt = System.currentTimeMillis();
    }

    /** A gauge step landed (or any step produced a number worth graphing): a point, and its text. */
    public static synchronized void sample(String id, String step, float value, String text) {
        long now = System.currentTimeMillis();
        Run r = run(id);
        Step s = r.step(step);
        s.gauge = -1f;
        s.waiting = false;
        s.push(value);
        s.lastText = text;
        s.firedAt = now;
        r.touchedAt = now;
    }

    /** The step will not happen this time; the panel shows it red with the reason. */
    public static synchronized void skip(String id, String step, String why) {
        note(id, "skip " + step + " - " + why);
        long now = System.currentTimeMillis();
        Run r = run(id);
        Step s = r.step(step);
        s.waiting = false;
        s.gauge = -1f;
        s.skippedAt = now;
        s.skipWhy = why;
        r.touchedAt = now;
    }

    /** One step's wait or gauge is dropped (its condition went away); the others carry on. */
    public static synchronized void clear(String id, String step) {
        Run r = RUNS.get(id);
        Step s = r == null ? null : r.find(step);
        if (s == null) return;
        s.waiting = false;
        s.gauge = -1f;
    }

    /** Nothing is in progress any more: every wait and gauge is dropped, the history kept. */
    public static synchronized void idle(String id) {
        Run r = RUNS.get(id);
        if (r == null) return;
        for (Step s : r.steps) {
            s.waiting = false;
            s.gauge = -1f;
        }
    }

    /** One line for the whole module, shown under its panel's header. */
    public static synchronized void state(String id, String text) {
        note(id, text);
        run(id).state = text == null ? "" : text;
    }

    // ---- listeners --------------------------------------------------------

    private static final java.util.List<Listener> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Something that hears every step a module fires or skips, and its state lines, as they
     * happen: an extension's recorder, say. Called on whatever thread the module traced from,
     * inside the trace's lock, so keep it short. An abstract class, not an interface (no lambdas).
     */
    public abstract static class Listener {
        /** {@code text}: {@code fire <step>}, {@code skip <step> - <why>}, or the module's state line. */
        public abstract void note(String id, String text);
    }

    /** Adds a {@link Listener}; registered once, from an init. */
    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    /** Every listener told; one that throws is dropped and logged, so a recorder can never break a module. */
    private static void note(String id, String text) {
        if (LISTENERS.isEmpty()) return;
        for (Listener l : LISTENERS) {
            try {
                l.note(id, text);
            } catch (RuntimeException | LinkageError e) {
                LISTENERS.remove(l);
                com.mineux.core.MineUX.LOGGER.warn("A trace listener failed and is off", e);
            }
        }
    }
}
