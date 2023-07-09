package bpipe

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

class NetRCTest {

    @Test
    void testParseNetRC() {
        
        File netRCFile = new File('src/test/data/netrc.test.txt')
        
        netRCFile.text = 
        '''
        machine foo.bar.com login  apiuser password mytestpassword
        '''.trim()
        
        
        NetRC netrc = NetRC.load(netRCFile)
        
        assert netrc.hosts.size() == 1
        assert netrc.hosts[0].machine == 'foo.bar.com'
        assert netrc.hosts[0].login == 'apiuser'
        assert netrc.hosts[0].password == 'mytestpassword'
    }

}
