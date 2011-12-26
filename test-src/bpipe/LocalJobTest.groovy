package bpipe;

import static org.junit.Assert.*;

import org.junit.Test;

class LocalJobTest {

    @Test
    public void testStart() {
        LocalCommandExecutor job = new LocalCommandExecutor()
        job.start("ls")
    }

    @Test
    public void testWaitFor() {
        
        LocalCommandExecutor job = new LocalCommandExecutor()
        long startTimeMs = System.currentTimeMillis()
        job.start("echo HELLO > tmp.txt; sleep 5;  echo FOO > tmp.txt")
        int returnCode = job.waitFor()
        
        String text = new File("tmp.txt").text.trim()
        assert text == "FOO"
        assert System.currentTimeMillis() - startTimeMs > 5000
        assert returnCode == 0
    }
    
    @Test
    public void testFailedCommand() {
        LocalCommandExecutor job = new LocalCommandExecutor()
        job.start("false")
        
        Thread.sleep(1000)
        assert job.waitFor() != 0
    }

    @Test
    public void testStatus() {
//        fail("Not yet implemented");
    }

}
