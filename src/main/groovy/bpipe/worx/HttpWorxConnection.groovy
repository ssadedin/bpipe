package bpipe.worx

import bpipe.Config
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import groovy.util.logging.Log;

/**
 * Convenience wrapper that gives a few useful additional methods for
 * sending HTTP protocol constructs.
 * 
 * @author Simon
 */
class HttpWriter {
    @Delegate
    Writer wrapped
    
    /**
     * Sends the output termintaed by an appropriate newline
     */
    HttpWriter headerLine(String line) {
      wrapped.print(line + "\r\n")
      return this
    }
    
    /**
     * Necessary because default print method comes from Object
     * and prints to stdout.
     * 
     * @param obj
     */
    void print(Object obj) {
        this.wrapped.print(obj)
    }
}


@Log
class HttpWorxConnection extends WorxConnection {
    /**
     * Underlying socket for connection to Worx server.
     * This is wrapped by #socketReader and #socketWriter.
     */
    Socket socket 
    
    /**
     * Reader for reading the socket
     */
    Reader socketReader
    
    /**
     * Writer for writing to socket
     */
    HttpWriter socketWriter
    
    /**
     * Count of number of times this connection failed
     */
    int failures = 0
    
    boolean closeAfterRead = false
   
    void sendJson(String path, Object payload) {
        sendJson(path, JsonOutput.toJson(payload))
    }
    
    void sendJson(String path, String eventJson) {
        
        if(socket == null || socket.isClosed())
            resetSocket()
            
        if(socket == null)
            return
            
        byte [] jsonBytes = eventJson.getBytes("UTF-8")
        
        log.info "POST $path" 
        
//        HttpWriter f = new HttpWriter(wrapped:new FileWriter("http.log"))
//        
//        f.headerLine("POST $path HTTP/1.1")
//                    .headerLine("Host: localhost:8080")
//                    .headerLine("Content-Type: application/json;charset=utf-8")
//                    .headerLine("Accept: */*")
//                    .headerLine("User-Agent: curl/7.50.0")
//                    .headerLine("Content-Length: " + jsonBytes.size()) // encoding?
//                    .headerLine("")
//                    .flush()
//  
//        f.print(eventJson+"\r\n") // note that encoding was set in creation of underlying Writer
//        f.headerLine("")
//         .flush()
//         
        
        socketWriter.headerLine("POST $path HTTP/1.1")
                    .headerLine("Host: localhost:8080")
                    .headerLine("Content-Type: application/json;charset=utf-8")
                    .headerLine("Accept: */*")
                    .headerLine("User-Agent: curl/7.50.0")
                    .headerLine("Content-Length: " + jsonBytes.size()) // encoding?
                    .headerLine("")
                    .flush()
        
        socketWriter.print(eventJson+"\r\n") // note that encoding was set in creation of underlying Writer
        socketWriter.headerLine("")
                    .flush()
    }
    
    /**
     * Read the HTTP response from the given reader.
     * First reads headers and observes content length header to 
     * then load the body. Requires content length to be set!
     * 
     * @param reader
     * @return
     */
    Object readResponse() {
        
        if(this.socketReader == null)
            return null
        
        log.info "Starting to read response"
        
        // Read headers
        String line
        int blankCount=0
        Map headers = [:]
        
        try {
            List<String> nonHeaderLines = []
            while(true) {
              line = socketReader.readLine()
//              log.fine "GOT RESPONSE line: " + line
              if(!line) {
                  break
              }
              if(line)
                  blankCount = 0
              List<String> header = line.tokenize(':')*.trim()
              if(header.size()>1)
                headers[header[0]] = header[1]
              else
                nonHeaderLines << line
            }
            
            // First non-header line should have HTTP status
            if(nonHeaderLines.isEmpty())
                throw new Exception("No HTTP response code returned from server!")
    
            List<String> statusLineFields = nonHeaderLines[0].tokenize()
            if(statusLineFields.size()<2)
                throw new Exception("HTTP status line does not have expected format: " + nonHeaderLines[0])
            
            Integer statusCode = statusLineFields[1].toInteger()
            if(statusCode >= 400)
                throw new Exception("Event post returned status code ${statusCode}:\n" + nonHeaderLines.join('\n'))
                
            Integer contentLength = headers['Content-Length']?.toInteger()?:0
            log.info "Content Length = " + contentLength
        
            if(contentLength > 0) {
                char [] buffer = new char[contentLength+1]
                socketReader.read(buffer)
                log.info "RAW REPONSE: \n" + buffer
                
                Object result = new JsonSlurper().parse(new StringReader(new String(buffer)))
                return result
            }
            else
            if(headers['Transfer-Encoding'] == 'chunked') {
                
                // Read the chunk length
                String chunkLengthValue = socketReader.readLine()
                Long chunkSize = Long.decode("0x" + chunkLengthValue)
                log.info "Chunk size in bytes: $chunkSize"
                
                char [] buffer = new char[chunkSize]
                socketReader.read(buffer)
                
                String payload = new String(buffer)
                
                String newLine = socketReader.readLine()
                String nextChunk = socketReader.readLine()
                if(nextChunk != "0")
                    log.warning("next chunk length has unexpected value: $nextChunk")
                
                socketReader.readLine() // consume final empty line
                
                return new JsonSlurper().parse(new StringReader(payload))
            }
            else {
                log.info "No response data"
                return [:]
            }
        }
        finally {
            if(this.socket) {
                log.info "Closing Worx connection by server request"
                this.socket.close()
                this.socket = null
            }
        }
    }
    
    void resetSocket() {
        
        log.info "Resetting Worx connection ..."
        try {
            socket.close()
        }
        catch(Exception e) {
            // Ignore
        }
        
//        log.info "Config is ${Config.userConfig}"
        
        String configUrl = Config.userConfig["worx"]?.url?:"http://127.0.0.1:8888/"
        log.info "Connecting to $configUrl"
        
        URL url = new URL(configUrl)
        try {
            socket = new Socket(url.host, url.port) 
        
            socketReader = new BufferedReader(new InputStreamReader(socket.inputStream))
            socketWriter = new HttpWriter(wrapped: new PrintWriter(new OutputStreamWriter(socket.outputStream, "US-ASCII")))
            
        } catch (Exception e) {
            if((failures%20) == 0) {
               println "WARNING: Worx connection to " + configUrl + " unsuccessful: " +  e.message
            }
            this.failures++
        }
    }
    
    void close() {
        try { socketReader.close() } catch(Exception e) { }
        try { socketWriter.close() } catch(Exception e) { }
        try { socket.close() } catch(Exception e) { }
    }
}
