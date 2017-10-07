package bpipe

import groovy.transform.CompileStatic
import groovy.util.logging.Log;

import java.io.Reader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern

class OutputLogEntry {
    String branch
    
    String commandId
    
    StringBuilder content 
}

@Log
class OutputLogIterator implements Iterator<OutputLogEntry> {
    
    Reader reader = null
    
    Map<String,OutputLogEntry> currentEntries = [:]
    
    List<OutputLogEntry> completeEntries = []
    
    StringBuilder buffer = new StringBuilder()
    
    OutputLogIterator(String logFile) {
        reader = new File(logFile).newReader()
    }
    
    OutputLogIterator(Reader r) {
        reader = r
    }
    
    /**
     * Legal characters in branch names are:
     * 
     * <li>A-Z, a-z
     * <li>0-9
     * <li>_, -
     * 
     * Should there be others?
     */
    static Pattern tagPattern = ~'^\\[([a-zA-Z0-9_-]*)\\.([0-9]*)\\]'
    
    @CompileStatic
    @Override
    public boolean hasNext() {
        // Read lines until the first character is a '['
        while(true) {
            String line = reader.readLine()
            if(line == null)
                break
                
            if(line.startsWith('[')) { 
                Matcher match = tagPattern.matcher(line)
                if(!match)
                    continue
                    
                String tag = line.substring(match.start(),match.end()-match.start())
                String contents = line.substring(match.end())
                OutputLogEntry entry = currentEntries[tag]
                if(!entry) {
                    if(!parseEndEntry(line)) {
                        tag = "[" + match.group(1) + "." + match.group(2) + "]"
                        entry = new OutputLogEntry()
                        entry.branch = match.group(1)
                        entry.commandId = match.group(2)
                        entry.content = new StringBuilder(contents)
                        currentEntries[tag] = entry
                    }
                }
                else {
                    entry.content.append('\n'+contents)
                }
            }
            
            if(!completeEntries.empty)
                return true
        }

        currentEntries.each { tag, ole ->
            completeEntries << ole
        }
        
        currentEntries.clear()
        
        return !completeEntries.empty
    }
    
    static Pattern endMarkerPattern = ~'^\\[([a-zA-Z0-9_]*)\\.([0-9]*).end\\]'
    
    /**
     * Future: currently we parse the whole log just to iterate through and emit
     * the first command entry. This is because there is no marker when a command ends
     * written to the log file. To avoid that, write a marker at the end of a command 
     * in the form of an empty line:
     * <p>
     * [branch.command_id.end]
     * <p>
     * This is not yet implemented, but the function here shows how the parsing would work
     * as a placeholder.
     * 
     * @param line
     * @return  true if the line matches an end marker for a command
     */
    boolean parseEndEntry(String line) {
        def endMatch = endMarkerPattern.matcher(line)
        if(endMatch) {
            String tag = "[" + endMatch[0][1] + "." + endMatch[0][2] + "]"
            OutputLogEntry entry = currentEntries[tag]
            if(entry) {
                completeEntries << entry
                currentEntries.remove(tag)
            }
            else {
                log.warning "End entry found for comand $tag but no recorded beginning for that command"
            }
        }
        else {
            return false // not an end-of-command-entry
        }
    }

    @Override
    public OutputLogEntry next() {
        def ole = completeEntries.remove(0)
        return ole
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException()
    }
    
    public static void main(String [] args) {
        String logFile = "/Users/simon/bpipe/tests/parallel_same_stage_outputs/.bpipe/logs/13089.log"
        new OutputLogIterator(logFile).each {
            println "Commnand $it.commandId had output $it.content"
        }
    }
}
