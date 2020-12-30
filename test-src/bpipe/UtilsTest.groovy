package bpipe;

import static org.junit.Assert.*;

import static bpipe.Utils.joinShellLines as jsl
import static bpipe.Utils.splitShellArgs as sp

import org.junit.Test;

import bpipe.storage.LocalPipelineFile
import groovy.time.TimeCategory

class UtilsTest {

    @Test
    public void testJoinShellLines() {
        
        // A simple command should not be changed!
        assert jsl("ls -l hello world") == "ls -l hello world"
        
        // command crossing lines should get joined
        assert jsl("hello\nworld") == "hello world"
        
        // commands separated by blank lines shoudl get joined with semicolon
        assert jsl("hello\n\nworld").matches("hello; *world")
        
        // commands separated but already ending in ; should not get an extra one
        assert jsl("hello;\n\nworld").matches("hello; *world")
        
        // commands separated but already ending in & should not get an extra one
        assert jsl("hello&\n\nworld").matches("hello& *world")
        
        // commands should have leading white space stripped and normalised to a single space
        // but not embedded spaces within a line
        def x = jsl("\n\thello\tthis\tcontains\ttabs\n\tworld this contains spaces\n\tthere")
        def y = "hello\tthis\tcontains\ttabs world this contains spaces there"
        println x
        println y
        assert x == "hello\tthis\tcontains\ttabs world this contains spaces there"
    }

    @Test
    public void testSplitShellArgs() {
        
        assert sp("foo bar") == ["foo","bar"]
        assert sp("foo\tbar") == ["foo","bar"]
        assert sp("tree 'foo bar'") == ["tree","foo bar"]
        assert sp("""tree 'foo "bar"'""") == ['tree','foo "bar"']
        assert sp("""tree 'foo \\' "bar"'""") == ['tree','foo \' "bar"']
        
    }
    
    @Test
    public void testWalltimeToMs() {
        use(TimeCategory) {
            assert Utils.walltimeToMs("1") == 1.seconds.toMilliseconds()
            assert Utils.walltimeToMs(1) == 1.seconds.toMilliseconds()
            assert Utils.walltimeToMs("00:30") == 30.seconds.toMilliseconds()
            assert Utils.walltimeToMs("2:00") == 2.minutes.toMilliseconds()
            assert Utils.walltimeToMs("1:3:13") == (1.hour + 3.minutes + 13.seconds).toMilliseconds()
            assert Utils.walltimeToMs("2:03:30:00") == (2.days + 3.hours + 30.minutes).toMilliseconds()
        }
    }
    
    @Test
    void testPrintTable() {
        println Utils.table(["foo","bar","cat"], [
            [1/3,"Fog","Tree"],
            [99/98,"Bar", "BonkerConker"]
        ])
        
        println Utils.table(["foo","bar","cat"], [
            [1/3,"Fog","Tree"],
            [99/98,"Bar", "BonkerConker"]
        ], format: [foo: '%.2f']) 
        
        println Utils.table(["foo","bar","time"], [
            [1/3,"Fog",[new Date(System.currentTimeMillis() - 2343243242), new Date()]],
            [99/98,"Bar", [new Date(System.currentTimeMillis() - 2342423), new Date()] ]
        ], format: [foo: '%.2f', time: "timespan"]) 
         
        
    }
    
    @Test
    void waitWithTimeoutTest() {
        
        String result 
        
        result = Utils.waitWithTimeout(5000L) {
            null
        }.ok {
            assert false
        }.timeout {
            println "Correct behavior of timeout"
            "timed out"
        }
        
        assert result == "timed out"
        
        
        result = Utils.waitWithTimeout(5000L) {
            true
        }.ok {
            println "Correct behavior of timeout"
            "hello"
        }.timeout {
            assert false
        } 
        
        assert result == "hello"
    }
    
    @Test
    void findOlderTest() {
        
        new File('src/test/data').mkdirs()

        def old = 'src/test/data/older.txt'
        new File(old).text = 'hello'
        Thread.sleep(2000)
        
        def newer ='src/test/data/newer.txt'
        new File(newer).text = 'world'
        
        def older = Utils.findOlder([new LocalPipelineFile(old)],[new LocalPipelineFile(newer)])
        
        assert older.size() == 1
        
        older = Utils.findOlder([new LocalPipelineFile(newer)],[new LocalPipelineFile(newer)])

        assert older.size() == 0
        
        older = Utils.findOlder([new LocalPipelineFile(newer)],[new LocalPipelineFile('src/test/data')])

        assert older.size() == 0
    }
    
    @Test
    void 'conversion of nested config objects to map'() {
       ConfigObject cfg = new ConfigObject()
       cfg.foo.bar.baz = [hello:'world']
       cfg.foo.tree = 5
        
       Map map = Utils.configToMap(cfg)
       
       assert map.foo
       assert map.foo instanceof Map
       assert map.foo.bar instanceof Map
       assert map.foo.bar.baz instanceof Map

       assert map.foo.bar.baz.hello == 'world'
    }
    
}
