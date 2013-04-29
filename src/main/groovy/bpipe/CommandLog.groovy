/*
 * Copyright (c) 2012 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
 package bpipe

import groovy.util.logging.Log;

/**
 * Responsible for committing commands to the command log.
 * <p>
 * At the moment this is really stunningly simple, and all it does
 * is ensures sychronisation (so we don't get garbled commands 
 * from multiple threads). However longer term it can get much much
 * more sophisticated. 
 * 
 * @author Simon
 */
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
