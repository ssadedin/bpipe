package bpipe;

import static org.junit.Assert.*;
import static FastUtils.*

import static FastUtils.globToRegex as gtr

import org.junit.Before;
import org.junit.Test;

class FastUtilsTest {

    @Before
    public void setUp() throws Exception {
    }
    
    @Test
    void testGlobToRegex() {
        
        def x = "hello.world.txt";
        
        assert gtr("*.txt").matcher(x).matches()
        assert gtr("hello*.txt").matcher(x).matches()
        assert gtr("hello.*.txt").matcher(x).matches()
        assert gtr("*.world.txt").matcher(x).matches()
        assert gtr("*.wo?ld.txt").matcher(x).matches()
        assert gtr("*.wo*ld.txt").matcher(x).matches()
        
        assert !gtr("*.txx").matcher(x).matches()
        assert !gtr("x*.txt").matcher(x).matches()
        assert !gtr("hello.wx*.txt").matcher(x).matches()
    }
    
    @Test
    void testTrim() {
        
        assert FastUtils.strip('.foo.','.') == 'foo'
        assert FastUtils.strip('..','.') == ''
        assert FastUtils.strip('...','.') == ''
        assert FastUtils.strip('','.') == ''
    }
}
