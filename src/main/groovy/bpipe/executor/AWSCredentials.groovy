package bpipe.executor

import java.util.regex.Pattern

import groovy.util.logging.Log

@Singleton
@Log
class AWSCredentials {
    
    Map<String, Map> keys = null
    
    /**
     * Read the AWS credentials from ~/.aws/credentials
     */
    void load() {
        
        try {
            
            keys = [:]
            
            Pattern name_pattern = ~'\\[(.*)\\]$'
            
            Map key = null
            
            def credsFile = new File( System.properties['user.home'] + '/.aws/credentials')
            if(!credsFile.exists())
                return

            List<String> creds = credsFile.text.readLines()

            creds.each { line ->
                
                if(line.trim().startsWith('#'))
                    return
                
                def match = line =~ name_pattern
                
                if(match)    {
                    key = [name: match[0][1]]
                    keys[key.name] =  key
                }
                
                line = line.replace(' ','')
                
                if(line.startsWith('aws_access_key_id=')) {
                    key.access_key_id = line.tokenize('=')[1]
                }
                
                if(line.startsWith('aws_secret_access_key=')) {
                    key.secret_access_key = line.tokenize('=')[1]
                }
            }
        }
        catch(Exception e) {
            log.warning("Attempt to load AWS Credentials failed: " + e.toString())
        }
    }
    
    static AWSCredentials getTheInstance() {
        if(AWSCredentials.instance.keys == null)
            AWSCredentials.instance.load()

        return AWSCredentials.instance
    }
}
