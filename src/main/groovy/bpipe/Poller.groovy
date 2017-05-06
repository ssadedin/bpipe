package bpipe

/**
 * A single shared timer used for scheduling periodic jobs
 * 
 * @author Simon Sadedin
 */
@Singleton
class Poller {
    
    Timer timer = new  Timer(true)

}
