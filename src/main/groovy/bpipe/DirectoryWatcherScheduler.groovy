package bpipe

import java.util.concurrent.*

import groovy.transform.CompileStatic

/**
 * A single shared timer used for scheduling periodic jobs
 * 
 * @author Simon Sadedin
 */
@Singleton
class DirectoryWatcherScheduler {

    ScheduledExecutorService executor = Executors.newScheduledThreadPool(Config.config.getOrDefault('schedulerThreads', 4))

    @CompileStatic
    static DirectoryWatcherScheduler getTheInstance() {
        DirectoryWatcherScheduler.instance
    }
}
