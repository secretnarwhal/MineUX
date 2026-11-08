package com.mineux.screens.net;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** A relay listener that keeps everything it is told, for tests to wait on. */
final class Recorder extends RelayClient.Listener {

    final LinkedBlockingQueue<Object> events = new LinkedBlockingQueue<>();
    volatile UUID you;
    volatile int connects;

    record Screens(List<ShareInfo> list) {}
    record Screen(ShareInfo info) {}
    record Gone(UUID id) {}
    record Viewers(UUID id, int n) {}
    record Error(String msg) {}
    record Refused() {}

    @Override
    public void onState(boolean connected) {
        if (connected) connects++;
    }

    @Override
    public void onWelcome(UUID id) {
        you = id;
    }

    @Override
    public void onScreens(List<ShareInfo> list) {
        events.add(new Screens(list));
    }

    @Override
    public void onScreen(ShareInfo screen) {
        events.add(new Screen(screen));
    }

    @Override
    public void onGone(UUID id) {
        events.add(new Gone(id));
    }

    @Override
    public void onViewers(UUID id, int viewers) {
        events.add(new Viewers(id, viewers));
    }

    @Override
    public void onFrame(Wire.Frame frame) {
        events.add(frame);
    }

    @Override
    public void onError(String message) {
        events.add(new Error(message));
    }

    @Override
    public void onRefused() {
        events.add(new Refused());
    }

    /** The first event (skipping others) that matches, within {@code seconds}; throws when none came. */
    @SuppressWarnings("unchecked")
    <T> T await(Class<T> type, Predicate<T> test, int seconds) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            Object e = events.poll(50, TimeUnit.MILLISECONDS);
            if (e != null && type.isInstance(e) && test.test((T) e)) return (T) e;
        }
        throw new AssertionError("no " + type.getSimpleName() + " within " + seconds + " s");
    }

    void waitConnected(RelayClient client, int seconds) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!client.connected()) {
            if (System.nanoTime() > end) throw new AssertionError("not connected within " + seconds + " s");
            Thread.sleep(20);
        }
    }
}
