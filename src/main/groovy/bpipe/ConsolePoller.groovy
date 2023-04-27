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
        if(System.console()== null)
            throw new IllegalStateException("Unable to access system console. Please ensure you are running Bpipe in a proper terminal")

        while(true) {
            String line = System.console().readLine()
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
