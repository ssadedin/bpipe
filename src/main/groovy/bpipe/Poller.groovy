package bpipe

import java.util.concurrent.*

/**
 * A single shared timer used for scheduling periodic jobs
 * 
 * @author Simon Sadedin
 */
@Singleton
class Poller {

    ScheduledExecutorService executor = Executors.newScheduledThreadPool(Config.config.getOrDefault('schedulerThreads', 2))

}
