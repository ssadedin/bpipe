package bpipe

import groovy.transform.CompileStatic

/**
 * In java there is no direct way to poll the console for input without blocking. Therefore,
 * this class implements a background thread that constantly reads from the console and
 * forwards the results to readers via the poll method.
 * 
 * @author simon.sadedin
 */
@Singleton
@CompileStatic
class ConsolePoller implements Runnable {

    List<String> buffer = Collections.synchronizedList([])
    
    void run() {
        Console console = System.console()
        if(console == null) {
            // No console available - thread will exit silently
            // This can happen in non-interactive environments (e.g., tests)
            return
        }

        while(true) {
            String line = console.readLine()
            if(line == null)
                return
            buffer << line
        }
    }
    
    String poll() {
        if(buffer.isEmpty())
            return null
        String result = buffer[0]
        buffer.clear()
        return result
    }
    
    static ConsolePoller getInstance() {
        return instance
    }
}
