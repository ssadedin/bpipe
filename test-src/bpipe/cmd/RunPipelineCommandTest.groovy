package bpipe.cmd

import static org.junit.Assert.*

import org.junit.Test

class RunPipelineCommandTest {
    
    File p

	def rpc = new RunPipelineCommand(["pipeline.groovy"])
    
	@Test
	public void 'test finding templated folder names'() {
        File p = testDir([
                            'foo_bar_001_baz',
                            'foo_bar_002_baz',
                            'foo_bar_007_baz',
                        ])
        
		List idxs = rpc.findChildDirectoryIndices(p, 'foo_bar_{inc}_baz')
        assert idxs.size() == 3
        assert idxs.max() == 7
    }
        
    @Test
	public void 'test folder without numeric'() {
       p = testDir([
            'foo_bar_001_baz',
            'foo_bar_002_baz',
            'foo_bar_cat_baz',
            'foo_bar_009_baz',
       ]) 
       
       List idxs = rpc.findChildDirectoryIndices(p, 'foo_bar_{inc}_baz')
       assert idxs.size() == 3
       assert idxs.max() == 9
	}
    
    @Test
	public void 'test mismatching prefix'() {
       p = testDir([
            'boo_bar_001_baz',
            'foo_bar_002_baz',
            'foo_bar_cat_baz',
            'coo_bar_009_baz',
       ]) 
       
       List idxs = rpc.findChildDirectoryIndices(p, 'foo_bar_{inc}_baz')
       assert idxs.size() == 1
       assert idxs.max() == 2
	}
    
    @Test
    void 'test simple directory increment'() {
       p = testDir([])
       
       List idxs = [1,3,5]
       def rpc = new RunPipelineCommand(["pipeline.groovy"]) {
            List<Integer> findChildDirectoryIndices(File parentFile, String template) {
                return idxs
            }
       }
       
       String dir = rpc.computeRunDirectoryPart(p, 'cat_dog_{inc}_tree')
       assert dir == 'cat_dog_0006_tree'
       
       idxs = [9999]
       dir = rpc.computeRunDirectoryPart(p, 'cat_dog_{inc}_tree')
       assert dir == 'cat_dog_10000_tree'
    }
    
    @Test
	public void 'test empty folder'() {
       p = testDir([])         
       List idxs = rpc.findChildDirectoryIndices(p, 'foo_bar_{inc}_baz')
       assert idxs.size() == 0
    }
    
    private File testDir(List<String> paths) {
        File p = new File('test') {
                    boolean exists() { true }
                    boolean isDirectory() { true }
                    File[] listFiles() {
                        paths.collect { new File(it) { boolean isDirectory() { true  }} }
                    }
                }
        return p
    }

}
