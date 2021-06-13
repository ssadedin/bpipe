package bpipe.agent

import java.io.IOException
import java.io.PrintStream
import java.io.Writer
import java.util.logging.Level
import java.util.logging.Logger

import bpipe.worx.WorxConnection
import groovy.transform.CompileStatic

@CompileStatic
class WorxStreamingPrintStream extends Writer {
    
    private static Logger log = Logger.getLogger("WorxStreamingPrintStream")
    
    @Delegate
    private Writer writer
    
    private WorxConnection worx
    
    private Long commandId
    
    private int pos = 0
    
    private char [] buffer = new char[512]
    
    private long lastFlushTimeMs = System.currentTimeMillis()
    
    WorxStreamingPrintStream(Long commandId, Writer writer, WorxConnection worx) {
        this.commandId = commandId;
        this.writer = writer;
        this.worx = worx
    }
    
    
    @Override
    public Writer append(CharSequence s) throws IOException {
        this.buffer(s.getChars(), 0, s.size())
        this.writer.write(s.toString())
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        
        this.buffer(csq.getChars(), start, end - start)
        this.writer.write(csq.toString(), start, end - start)
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        
        log.info "Append: $cbuf"
        
        this.writer.write(cbuf, off, len)
        
        this.buffer(cbuf, off, len)
    }
        
    public void buffer(char[] cbuf, int off, int len) throws IOException {
        if(pos + len < buffer.size()) {
            if(log.isLoggable(Level.FINE)) {
                log.fine "Buffer $len chars (pos=$pos / ${buffer.size()})"
            }
            System.arraycopy(cbuf, off, buffer, pos, len)
            pos += len
            return
        }
        
        if(pos > 0) {
            flushToWorx()
            this.buffer(cbuf, off, len)
        }
        else {
            log.info "Flush raw pass through buffer"
            flushToWorx(cbuf, off, len)
        }
    }
    
    String flushToWorx() {
        
        this.writer.flush()
        
        try {
            flushToWorx(buffer,0,pos)
        }
        finally {
            pos = 0
        }
            
       return worx.readResponse() 
         
    }
    
    void flushToWorx(char[] cbuf, int off, int len) {
        
        log.info "Flush ${len} chars to upstream connection"
        
        worx.sendJson("/commandResult/$commandId",
                [
                    command: commandId,
                    status: "ok",
                    partial: true,
                    output: new String(cbuf, off, len)
                ]
        )
    }

}
