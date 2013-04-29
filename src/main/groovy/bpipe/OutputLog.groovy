/*
 * Copyright (c) 2013 MCRI, authors
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

import java.io.IOException;

import groovy.util.logging.Log;

/**
 * Responsible for printing messages to the main output log, allowing
 * some messages to be buffered until explicitly flushed, or an
 * auto-flushed messages is printed.
 * <p>
 * The purpose of buffering is to allow messages to be pre-emptively added,
 * but not ever be flushed if a command doesn't actually
 * get executed. That in turn is a workaround for the 
 * problem that the pipeline doesn't really skip a 
 * whole stage in many cases, it only actually skips
 * the commands, which makes it hard for a user to 
 * output a message that only appears when the commands
 * are going to be executed.
* 
 * @author Simon
 */
@Log
class OutputLog implements Appendable {
    
    /**
     * The actual buffer
     */
    StringBuilder buffer = new StringBuilder()
    
    final static char NEWLINE = '\n' as char
    
    /**
     * Buffer a message to be printed later, 
     * if and when messages are flushed
     * 
     * @param output
     */
    void buffer(String output) {
        buffer.append(output)
        buffer.append(NEWLINE)
    }
    
    void flush(String output) {
        println buffer
        println output
        buffer.setLength(0)
    }
    
    void flush() {
        println buffer
        buffer.setLength(0)
    }

    @Override
    public Appendable append(CharSequence arg0) throws IOException {
        buffer.append(arg0)
        this.flush()
        return this;
    }

    @Override
    public Appendable append(char arg0) throws IOException {
        buffer.append(arg0);
        this.flush();
        return this;
    }

    @Override
    public Appendable append(CharSequence arg0, int arg1, int arg2) {
        buffer.append(arg0,arg1,arg2)
        this.flush()
        return this;
    }
}
