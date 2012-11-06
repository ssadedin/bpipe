package bpipe

import org.junit.Before;
import org.junit.Test

class DependenciesTest {
    
    Properties a,b,c,d
    
    @Before
    void setUp() {
        
        // 
        // A =>
        //    B 
        //    C =>
        //       D
        println "Setting up"
        a = new Properties()
        a.outputFile = 'a.txt'
        a.inputs = ['input1.txt']
        
        b = new Properties()
        b.outputFile = 'b.txt'
        b.inputs = ['a.txt']
        
        c = new Properties()
        c.outputFile = 'c.txt'
        c.inputs = ['a.txt']
        
        d = new Properties()
        d.outputFile = 'd.txt'
        d.inputs = ['c.txt']
    }
    
//    @Test
//    void testComputeOutputGraph() {
//        
//       
//       def result = Dependencies.instance.computeOutputGraph([a,b,c,d]) 
//       
//       println result.dump()
//       
//       assert result.values*.inputs.flatten() == ['input1.txt']
//       
//       assert result.children.size() == 2
//       
//       def bEntry = result.entryFor("b.txt")
//       
//       assert bEntry != null
//       assert bEntry.parents.contains(result)
//       assert bEntry.children.isEmpty()
//       
//       def cEntry = result.entryFor("c.txt")
//       def dEntry = result.entryFor("d.txt")
//       assert cEntry.parents.contains(result)
//       assert cEntry.children.contains( dEntry )
//       
//    }
//    
//    @Test
//    void testSpanningDependency() {
//        
//        def e = new Properties()
//        e.outputFile = 'e.txt'
//        e.inputs = ['a.txt','d.txt']
//        def result = Dependencies.instance.computeOutputGraph([a,b,c,d,e])
//        println "Spanning:"
//        println result.dump()
//        
//        def eEntry = result.entryFor('e.txt')
//        def aEntry = result.entryFor('a.txt')
//        def dEntry = result.entryFor('d.txt')
//        assert eEntry.parents.contains(aEntry)
//        assert eEntry.parents.contains(dEntry)
//    }
//    
//    @Test
//    void testEmptyGraph() {
//       def result = Dependencies.instance.computeOutputGraph([]) 
//       assert result.values.isEmpty()
//       assert result.children.isEmpty()
//    }
//    
//    @Test
//    void testSingleNode() {
//       def result = Dependencies.instance.computeOutputGraph([a]) 
//       assert result.values == [a]
//       assert result.children.isEmpty()
//    }
//    
//    @Test 
//    void testFindLeaves() {
//       def result = Dependencies.instance.computeOutputGraph([a,b,c,d]) 
//       def leaves = Dependencies.instance.findLeaves(result)
//       assert leaves*.values.flatten()*.outputFile.sort() == ['b.txt','d.txt']
//    }
//    
    @Test
    void testClone() {
       def result = Dependencies.instance.computeOutputGraph([a,b,c,d]) 
       GraphEntry cEntry = result.entryFor("c.txt")
       GraphEntry filtered = result.filter("c.txt") 
       GraphEntry cloned = filtered.entryFor("c.txt")
       
       println "Filtered:\n" + filtered.dump()
       
       assert cloned.parents.size() == cEntry.parents.size()
       assert cloned.parents*.values == cEntry.parents*.values
       assert cloned.children.size() == cEntry.children.size()
       assert ! cloned.children.is(cEntry.children)
       assert cloned.parents[0].entryFor('b.txt') == null
    }
    
}