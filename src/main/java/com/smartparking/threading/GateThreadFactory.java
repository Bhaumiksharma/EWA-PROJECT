package com.smartparking.threading;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Names the gate threads Gate-1, Gate-2, ... so the console output shows which gate admitted a car. */
public class GateThreadFactory implements ThreadFactory {

    private final AtomicInteger counter = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable task) {
        return new Thread(task, "Gate-" + counter.getAndIncrement());
    }
}
