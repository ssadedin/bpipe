package bpipe;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * Substitute text dumper for the groovy one which 
 * prints ugly messages when IO exceptions occur
 * 
 * @author simon.sadedin
 */
class TextDumper implements Runnable {
    
    final static Logger log = Logger.getLogger("bpipe.TextDumper");

    final InputStream in;
    final Appendable app;
    final String source;
    
    public TextDumper(String source, InputStream in, Appendable app) {
        this.in = in;
        this.app = app;
        this.source = source;
    }

    public void run() {
        InputStreamReader isr = new InputStreamReader(in);
        BufferedReader br = new BufferedReader(isr);
        String next;
        try {
            while ((next = br.readLine()) != null) {
                if (app != null) {
                    app.append(next);
                    app.append("\n");
                }
            }
        } catch(Exception e) {
            log.warning("Error while reading command output stream for " + source + " " + e.toString());
        }
    }
    
    static void consumeProcessOutput(String source, Process self, Appendable output, Appendable error) {
        consumeProcessOutputStream(source, self, output);
        consumeProcessErrorStream(source, self, error);
    }
    
    public static Thread consumeProcessErrorStream(String source, Process self, Appendable error) {
        Thread thread = new Thread(new TextDumper(source, self.getErrorStream(), error));
        thread.setName(source);
        thread.start();
        return thread;
    }

    public static Thread consumeProcessOutputStream(String source, Process self, Appendable output) {
        Thread thread = new Thread(new TextDumper(source, self.getInputStream(), output));
        thread.setName(source);
        thread.start();
        return thread;
    }
}
