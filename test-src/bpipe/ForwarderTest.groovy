package bpipe;

import static org.junit.Assert.*;

import org.junit.Test;

class ForwarderTest {

    // TODO use mocks to mock files
    @Test
    void testForward() {
        File f = new File("test.src.out")
        File outFile = new File("test.out")
        def forwarder = new Forwarder(f, new FileOutputStream(outFile))

        forwarder.forward(f, new FileOutputStream(outFile))
        // forwarder.forward(new File("test.src.out"),System.out)

        Timer timer = new Timer()
        timer.schedule(forwarder, 100, 500)

        def os = new FileOutputStream(f, true)
        os.withStream {
            it << "Figgle\n"
        }

        Thread.sleep(2000)

        assert outFile.text.trim() == "Figgle"

        new FileOutputStream(f, true).withStream {
            it << "Foo\n"
        }

        Thread.sleep(3000)

        timer.cancel()

    }
}
