package bpipe

import groovy.util.logging.Log;

@Log
class CommandLog {
    
    static FileWriter writer = new FileWriter("commandlog.txt", true)
    
    static CommandLog cmdLog = new CommandLog()
    
    /**
     * Write a line to the command log.
     */
    synchronized void write(String line) {
        writer.println(line)
        writer.flush()
    }
    
    void leftShift(String line) {
        write(line)
    }
}
