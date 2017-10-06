package bpipe

import org.junit.Test;



class OutputLogIteratorTest {
    
    String testLog =
"""
[----]  ====================================================================================================
[----]  |                              Starting Pipeline at 2015-10-10 19:05                               |
[----]  ====================================================================================================
take_me inputs=s_1.txt
how_are inputs=s_1.txt
[0.43]  how_are_you_stage
[0.42]  take_me_to_your_leader_stage
[0.44]  cp: illegal option -- u
[0.44]  usage: cp [-R [-H | -L | -P]] [-fi | -n] [-apvX] source_file target_file
[0.44]         cp [-R [-H | -L | -P]] [-fi | -n] [-apvX] source_file ... target_directory
[0.45]  cp: illegal option -- u
[0.45]  usage: cp [-R [-H | -L | -P]] [-fi | -n] [-apvX] source_file target_file
[0.45]         cp [-R [-H | -L | -P]] [-fi | -n] [-apvX] source_file ... target_directory
ERROR: Command failed with exit status = 64 : 

cp -uv s_1.txt.how_are_you s_1.txt.how_are_you.a 

ERROR: Command failed with exit status = 64 : 

cp -uv s_1.txt.take_me_to_your_leader s_1.txt.take_me_to_your_leader.a 


========================================= Pipeline Failed ==========================================

One or more parallel stages aborted. The following messages were reported: 

Branch 1.1 in stage Unknown reported message:

Command failed with exit status = 64 : 

cp -uv s_1.txt.how_are_you s_1.txt.how_are_you.a

Branch 1.2 in stage Unknown reported message:

Command failed with exit status = 64 : 

cp -uv s_1.txt.take_me_to_your_leader s_1.txt.take_me_to_your_leader.a
"""
    
    
    @Test
    void testParse() {
        
        def i = new OutputLogIterator(new StringReader(testLog)) 
        
        assert i.hasNext()
        
        
        i.each { ole ->
            println ole.commandId.center(80,"=")
            println ole.content
            
            println "=" * 80
        }
        
    }
    
    @Test
    void testComplexBranches() {
        
        String line = "[170914_NB501544_0178_ML171430_17W000588_STAR-20170831_SSQXTCRE.1581]\t##### ERROR A USER ERROR has occurred (version 3.6-0-g89b7209):\n"
        
        def i = new OutputLogIterator(new StringReader(line)) 
        assert i.hasNext()
        
        OutputLogEntry ole = i.next()
        println ole.commandId
        
        assert ole.commandId == "1581"
    }
}