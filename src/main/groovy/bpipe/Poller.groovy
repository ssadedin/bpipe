package bpipe

import java.util.concurrent.*

import groovy.transform.CompileStatic

/**
 * A single shared timer used for scheduling periodic jobs
 * 
 * @author Simon Sadedin
 */
@Singleton
class Poller {

    ScheduledExecutorService executor = Executors.newScheduledThreadPool(Config.config.getOrDefault('schedulerThreads', 2))

    @CompileStatic
    static Poller getTheInstance() {
        Poller.instance
    }
}
