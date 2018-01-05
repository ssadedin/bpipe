package bpipe

import bpipe.storage.LocalFileSystemStorageLayer
import bpipe.storage.StorageLayer
import java.nio.file.Path
import org.junit.After
import org.junit.Before
import org.junit.Test

class FileGlobberTest {
    
    File dir = new File(".globtest")
    
    FileGlobber globber = new FileGlobber(storage:new LocalFileSystemStorageLayer()) 
    
    @Before
    void before() {
        dir.mkdirs()
        new File(dir, "foo.txt").text = "bar"
    }
    
    @Test
    void testNoGlob() {
        assert globber.glob(~".globtest/foo.txt") == [".globtest/foo.txt"]
        assert globber.glob(".globtest/foo.txt") == [".globtest/foo.txt"]
    }
    
    @Test
    void testGlob() {
        assert globber.glob(~".globtest/.*.txt") == [".globtest/foo.txt"]
        assert globber.glob(".globtest/*.txt") == [".globtest/foo.txt"]        
    }
    
    @After
    void after() {
        dir.deleteDir()
    }
}