package bpipe 

import java.util.concurrent.Semaphore

import org.junit.Before
import org.junit.Test;

class ConcurrencyTest {
    
    bpipe.Concurrency c = bpipe.Concurrency.instance
    
    @Before
    void setup() {
        Config.userConfig = new ConfigObject()
        Config.userConfig.max_per_command_threads = 12
    }

    @Test
    void testAllocateResources() {
        
        def reqs = [1,4,5].collect { new ResourceRequest(resource: new ResourceUnit(key:"threads", amount:it)) }
        c.resourceRequests = reqs.clone()
        synchronized(c.resourceRequests) {
            c.allocateResources(new Semaphore(8))
            assert reqs*.allocated*.amount == [1,4,5]
        }
    }
    
    @Test
    void testAllocateUnlimitedResourcesWithEnough() {
        def reqs = [1,4,ResourceUnit.UNLIMITED].collect { new ResourceRequest(resource: new ResourceUnit(key:"threads", amount:it)) }
        c.resourceRequests = reqs.clone()
        synchronized(c.resourceRequests) { c.allocateResources(new Semaphore(8)) }
        assert reqs*.allocated*.amount == [1,4,3]
    }
    
    @Test
    void testAllocateUnlimitedResourcesWithNotEnough() {
        def reqs = [1,4,ResourceUnit.UNLIMITED].collect { new ResourceRequest(resource: new ResourceUnit(key:"threads", amount:it)) }
        c.resourceRequests = reqs.clone()
        synchronized(c.resourceRequests) { c.allocateResources(new Semaphore(4)) }
        assert reqs*.allocated*.amount == [1,4,1]
    }
    
    @Test
    void testShareBetweenUnlimitedRequestors() {
        def reqs = [1,4,ResourceUnit.UNLIMITED, ResourceUnit.UNLIMITED].collect { new ResourceRequest(resource: new ResourceUnit(key:"threads", amount:it)) }
        c.resourceRequests = reqs.clone()
        synchronized(c.resourceRequests) { c.allocateResources(new Semaphore(20)) }
        assert reqs*.allocated*.amount == [1,4,8,7]
    }
}