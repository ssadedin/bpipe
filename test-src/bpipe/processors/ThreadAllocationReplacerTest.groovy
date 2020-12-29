package bpipe.processors

import static org.junit.Assert.*

import org.junit.Test
import bpipe.*

class ThreadAllocationReplacerTest {
    
    ThreadAllocationReplacer tar = new ThreadAllocationReplacer()
    
    Command cmd
    
    static ru(Map args) {
        new ResourceUnit(args)
    }

    @Test
    public void 'threads specified with no threads variable reference'() {
        
        // Command does not use $threads and specific resource applied
        cmd = new Command(command:'hello world')
        assert tar.isUnlimited(cmd, [:], new ResourceUnit(key:"procs", amount:32)) == false
    }
    
    @Test
    void 'threads unspecified and threads variable referenced'() {
        
        // Command uses $threads and there is no specific thread resource applied
        cmd = new Command(command:"hello $PipelineContext.THREAD_LAZY_VALUE world")
        assert tar.isUnlimited(cmd, [:], new ResourceUnit(key:"procs", amount:0)) == true
    }

    @Test
    void 'threads specified and threads variable referenced'() {
        cmd = new Command(command:"hello $PipelineContext.THREAD_LAZY_VALUE world")
        assert tar.isUnlimited(cmd, [:], new ResourceUnit(key:"procs", amount:8)) == false
    }
    
    @Test
    void 'procs in config and threads variable referenced'() {
        cmd = new Command(command:"hello $PipelineContext.THREAD_LAZY_VALUE world")
        assert tar.isUnlimited(cmd, [procs:16], null) == false
    }
    
    @Test
    void 'procs in config and threads variable unreferenced'() {
        cmd = new Command(command:"hello world")
        assert tar.isUnlimited(cmd, [procs:16], null) == false
    }
    
    @Test
    void 'procs range in config and threads variable unreferenced'() {
        cmd = new Command(command:"hello world")
        assert tar.isUnlimited(cmd, [procs:4..8], ru(key:"threads", amount:4, maxAmount:8 )) == false
    }
    
}
