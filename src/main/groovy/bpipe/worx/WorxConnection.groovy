package bpipe.worx

import groovy.json.JsonOutput

abstract class WorxConnection {
    
    /**
     * Send the given JSON with the given path as destination
     * 
     * @param path
     * @param json
     */
    abstract void sendJson(String path, String json)
    
    /**
     * Return the response to the last JSON request sent
     * 
     * @return
     */
    abstract Object readResponse() 
    

    abstract void close() 
    
    /**
     * Default implementaiton to convert payload to JSON
     * @param path
     * @param payload
     */
    void sendJson(String path, Object payload) {
        sendJson(path, JsonOutput.toJson(payload))
    }
}
