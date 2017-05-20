package bpipe;

import static org.junit.Assert.*;

import static Utils.joinShellLines as jsl
import static Utils.splitShellArgs as sp

import org.junit.Test;

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
        assert Utils.walltimeToMs("1") == 1000L
        assert Utils.walltimeToMs(1) == 1000L
        assert Utils.walltimeToMs("00:30") == 1800000L
        assert Utils.walltimeToMs("2:00") == 7200000L
        assert Utils.walltimeToMs("1:3:13") == 24 * 3600000L + 3 * 3600000L + 13 * 60000L
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
}
