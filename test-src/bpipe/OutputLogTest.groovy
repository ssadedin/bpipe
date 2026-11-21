package bpipe

import org.junit.Test

class OutputLogTest {

    @Test
    void 'tail lines are limited to the most recent lines'() {
        OutputLog log = new OutputLog((String)null)
        (1..10).each { log.bufferLine("line $it") }
        assert log.getLastTailLines() == (6..10).collect { "line $it".toString() }
    }

    @Test
    void 'tail lines can be read while output is being appended'() {
        OutputLog log = new OutputLog((String)null)
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean(false)
        Throwable error = null

        // Simulates the thread forwarding command output while errors are summarised
        Thread writer = Thread.start {
            int i = 0
            while(!done.get()) {
                log.bufferLine("line ${i++}")
                if(i % 1000 == 0)
                    log.buffer.setLength(0) // keep memory bounded
            }
        }

        try {
            long end = System.currentTimeMillis() + 1000
            while(System.currentTimeMillis() < end) {
                try {
                    def lines = log.getLastTailLines()
                    lines.join('\n')
                    assert lines.size() <= log.maxTailLines
                }
                catch(Throwable t) {
                    error = t
                    break
                }
            }
        }
        finally {
            done.set(true)
            writer.join()
        }

        assert error == null : "Reading tail lines failed: $error"
    }
}
