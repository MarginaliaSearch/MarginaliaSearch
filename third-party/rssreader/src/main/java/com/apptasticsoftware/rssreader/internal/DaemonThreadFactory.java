package com.apptasticsoftware.rssreader.internal;

import java.util.concurrent.ThreadFactory;

/**
 * Thread factory that creates daemon threads
 */
public class DaemonThreadFactory implements ThreadFactory {
    private final String name;
    private int counter;

    public DaemonThreadFactory(String name) {
        this.name = name;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, name + "-" + counter++);
        t.setDaemon(true);
        return t;
    }

}
