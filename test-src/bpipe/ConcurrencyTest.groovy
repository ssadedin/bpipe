package bpipe 

import java.util.concurrent.Semaphore
import org.junit.Test;

class ConcurrencyTest {
    
    bpipe.Concurrency c = bpipe.Concurrency.instance
    
    @Test
    void testAllocateResources() {
        def reqs = [1,4,5].collect { new ResourceRequest(resource: new ResourceUnit(key:"threads", amount:it)) }
        c.resourceRequestors = reqs
        c.allocateResources(new Semaphore(8))
        
        assert reqs*.allocated*.amount == [1,4,5]
        
    }
    
    @Test
    void testAllocateUnlimitedResourcesWithEnough() {
        def reqs = [1,4,ResourceUnit.UNLIMITED].collect { new ResourceRequest(resource: new ResourceUnit(key:"threads", amount:it)) }
        c.resourceRequestors = reqs
        c.allocateResources(new Semaphore(8))
        assert reqs*.allocated*.amount == [1,4,3]
    }
    
    @Test
    void testAllocateUnlimitedResourcesWithNotEnough() {
        def reqs = [1,4,ResourceUnit.UNLIMITED].collect { new ResourceRequest(resource: new ResourceUnit(key:"threads", amount:it)) }
        c.resourceRequestors = reqs
        c.allocateResources(new Semaphore(4))
        assert reqs*.allocated*.amount == [1,4,1]
    }
    
    @Test
    void testShareBetweenUnlimitedRequestors() {
        def reqs = [1,4,ResourceUnit.UNLIMITED, ResourceUnit.UNLIMITED].collect { new ResourceRequest(resource: new ResourceUnit(key:"threads", amount:it)) }
        c.resourceRequestors = reqs
        c.allocateResources(new Semaphore(20))
        assert reqs*.allocated*.amount == [1,4,8,7]
    }
}