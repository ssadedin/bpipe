package bpipe;

import static org.junit.Assert.*;

import org.junit.Test;

class ToolDatabaseTest {
	
	/**
	 * We want tools to NOT be identified 
	 * as substrings of other commands/tools. In other
	 * words, a command should only be identified if it
	 * is surrounded by tokens that bash would consider 
	 * delimiters. 
	 */
	@Test
	void testCommandContainsTool() {
		
		def td = ToolDatabase.instance
		
		assert td.commandContainsTool("ls", "ls")
		assert td.commandContainsTool("ls -lh", "ls")
		assert td.commandContainsTool("nice ls -lh", "ls")
		assert td.commandContainsTool("nice /bin/ls -lh", "ls")
		
		assert !td.commandContainsTool("nice gls -lh", "ls")
		assert !td.commandContainsTool("nice lsg -lh", "ls")
		assert !td.commandContainsTool("nice ls-lh", "ls")
		assert !td.commandContainsTool("nice ls_lh", "ls")
		assert !td.commandContainsTool("nice _ls", "ls")
		
		assert td.commandContainsTool("java -jar C:/cygwin/home/ssadedin/gatk/workspace/gatks/GenomeAnalysisTK-1.5-32-g2761da9/GenomeAnalysisTK.jar -T Help","GenomeAnalysisTK")
		
	}
	
	@Test
	void testExpandToolName(){
		
		ConfigObject cfg = new ConfigObject()
		cfg.put("probe", "echo 1.0")
        
		
		Tool t = new Tool("foo", cfg)
		
		assert t.expandToolName("foo") == "foo"
		assert t.expandToolName("/bin/foo") == "/bin/foo"
		assert t.expandToolName("/bin/foo hello world") == "/bin/foo"
		assert t.expandToolName("fubar /bin/foo hello world") == "/bin/foo"
		
		assert t.expandToolName("fubar foo.jar hello world") == "foo.jar"
		assert t.expandToolName("fubar /bin/foo.jar hello world") == "/bin/foo.jar"
		
		t = new Tool("GenomeAnalysisTK", cfg)
		assert "C:/cygwin/home/ssadedin/gatk/workspace/gatks/GenomeAnalysisTK-1.5-32-g2761da9/GenomeAnalysisTK.jar" == t.expandToolName("java -jar C:/cygwin/home/ssadedin/gatk/workspace/gatks/GenomeAnalysisTK-1.5-32-g2761da9/GenomeAnalysisTK.jar -T Help")
	}
    
    @Test
    void testExpandWithExe() {
        def coniferCfg = new ConfigObject()
        coniferCfg.put("installExe","conifer.py")
        Tool t = new Tool("conifer", coniferCfg)
        
        assert t.expandToolName("python tools/conifer/0.2.2/conifer.py") == "tools/conifer/0.2.2/conifer.py"
    }
}
