package bpipe

import groovy.transform.CompileStatic
import groovy.transform.ToString

@ToString(excludes=['password'])
class NetRCHost {
    String machine
    String login
    String password
}

/**
 * Support to parse .netrc files to access authentication credentials for APIs etc
 * 
 * @author simon.sadedin
 */
class NetRC {
    
    List<NetRCHost> hosts
    
    /**
     * Parse the netrc file from the home directory
     */
    static NetRC load() {
         return load(new File(System.properties['user.home'], '.netrc'))
    }
    
    /**
     * Parse the given netrc file
     */
    static NetRC load(File netrcFile) {
        List<List<String>> hosts = 
            netrcFile
            .readLines()
            *.tokenize() // tokenize the whole file by white space, accepts both multiline and single line format
            .flatten()
            .collate(6) // each .netrc entry has 6 tokens (machine,login,password)
            *.collate(2) // the 6 tokens are 3 sets of key/value pairs, so collate those
            *.collectEntries() // turn into map
            .collect {
                new NetRCHost(it) // utilising map constructor
            }
        return new NetRC(hosts: hosts)
    }
}
