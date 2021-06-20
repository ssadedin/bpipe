package bpipe

import org.junit.Before;
import org.junit.Test

import bpipe.storage.LocalPipelineFile

class DependenciesTest {

    OutputMetaData a,b,c,d

    static {
        Runner.initializeLogging("tests")
    }

    @Before
    void setUp() {

        //
        // A =>
        //    B
        //    C =>
        //       D
        println "Setting up"
        a = testFile('a.txt','input1.txt')
        b = testFile('b.txt',a)
        c = testFile('c.txt',a)
        d = testFile('d.txt',c)
    }

    OutputMetaData testFile(def name, def input) {
        def a = new OutputMetaData()

        // a.outputFile = [ getName: { name }, exists: { true } ] as File
        a.outputFile = new LocalPipelineFile(name) {
            boolean existsOverride = true;
            boolean exists() { existsOverride }
        }

        a.outputPath = name
        a.cleaned = false

        if(!(input instanceof List))
            input = [input]

        a.inputs = []
        for(def inp in input) {
            if(inp instanceof String)  {
                a.inputs << inp
                a.timestamp = 0
            }
            else {
                a.inputs << inp.outputFile.name
                a.timestamp = Math.max(inp.timestamp + 1, inp.timestamp)
                println " Test output name = $name : time= $a.timestamp"
            }
        }
        if(!a.timestamp)
            a.timestamp = 0
        return a
    }


    @Test
    void testComputeOutputGraph() {


       GraphEntry result = Dependencies.instance.computeOutputGraph([a,b,c,d])

       println result.dump()

       assert result.children[1].values*.inputs.flatten() == ['input1.txt']

       assert result.children[1].children.size() == 2

       def bEntry = result.entryFor(new File("b.txt"))

       assert bEntry != null
       assert bEntry.parents.contains(result)
       assert bEntry.children.isEmpty()

       def cEntry = result.entryFor(new File("c.txt"))
       def dEntry = result.entryFor(new File("d.txt"))
       assert cEntry.parents.contains(result)
       assert cEntry.children.contains( dEntry )
    }

    @Test
    void testSpanningDependency() {

        def e = testFile('e.txt', [a,d])
        def result = Dependencies.instance.computeOutputGraph([a,b,c,d,e])
        println "Spanning:"
        println result.dump()

        def eEntry = result.entryFor(new File('e.txt'))
        def aEntry = result.entryFor(new File('a.txt'))
        def dEntry = result.entryFor(new File('d.txt'))
        assert eEntry.parents.contains(aEntry)
        assert eEntry.parents.contains(dEntry)
    }

    @Test
    void testOutputWithNoInputs() {

        def e = testFile('e.txt', [])
        def result = Dependencies.instance.computeOutputGraph([a,b,c,d,e])
        println "Graph:"
        println result.dump()

        def eEntry = result.entryFor(new File('e.txt'))
        def aEntry = result.entryFor(new File('a.txt'))
        def dEntry = result.entryFor(new File('d.txt'))
        def bEntry = result.entryFor(new File('b.txt'))
        def cEntry = result.entryFor(new File('c.txt'))

        assert !eEntry.parents.contains(aEntry)
        assert !eEntry.parents.contains(dEntry)
        assert !eEntry.parents.contains(bEntry)
        assert !eEntry.parents.contains(cEntry)

        assert !eEntry.children.contains(aEntry)
        assert !eEntry.children.contains(dEntry)
        assert !eEntry.children.contains(bEntry)
        assert !eEntry.children.contains(cEntry)
    }

    @Test
    void testEmptyGraph() {
       def result = Dependencies.instance.computeOutputGraph([])
       assert result.values.isEmpty()
       assert result.children.isEmpty()
    }

    @Test
    void testSingleNode() {
       def result = Dependencies.instance.computeOutputGraph([a]).children[0]
       assert result.values == [a]
       assert result.children.isEmpty()
    }

    @Test
    void testFindLeaves() {
       def result = Dependencies.instance.computeOutputGraph([a,b,c,d])
       def leaves = Dependencies.instance.findLeaves(result)
       assert leaves*.values.flatten()*.outputFile*.name.sort() == ['b.txt','d.txt']
    }

    @Test
    void testClone() {
       def result = Dependencies.instance.computeOutputGraph([a,b,c,d])

       println "Unfiltered:\n" + result.dump()


       GraphEntry cEntry = result.entryFor(new File("c.txt"))
       GraphEntry filtered = result.filter("c.txt")
       GraphEntry cloned = filtered.entryFor(new File("c.txt"))

       println "Filtered:\n" + filtered.dump()

       assert cloned.parents.size() == cEntry.parents.size()
       assert cloned.parents*.values == cEntry.parents*.values
       assert cloned.children.size() == cEntry.children.size()
       assert ! cloned.children.is(cEntry.children)
       assert cloned.parents[0].entryFor(new File('b.txt')) == null
    }

    @Test
    void testUpToDate() {
       def result
       result = Dependencies.instance.computeOutputGraph([a,b,c,d])
       assert a.upToDate
       assert b.upToDate
       assert c.upToDate
       assert d.upToDate

       // Let's make a be newer than the other files
       def oldts = a.timestamp
       a.timestamp = 100
       result = Dependencies.instance.computeOutputGraph([a,b,c,d])
       assert !b.upToDate
       assert !c.upToDate
       assert !d.upToDate

       a.timestamp = oldts
       oldts = b.timestamp
       b.timestamp = 100
       result = Dependencies.instance.computeOutputGraph([a,b,c,d])

       // Nothing depends on B so updating it should not change the fact that
       // everything else stays up to date
       assert a.upToDate
       assert b.upToDate
       assert c.upToDate
       assert d.upToDate

       b.timestamp = oldts

       oldts = c.timestamp
       c.timestamp = 100
       result = Dependencies.instance.computeOutputGraph([a,b,c,d])

       // d depends on c, so it should now be out of date, but a,b,c should be ok
       assert a.upToDate
       assert b.upToDate
       assert c.upToDate
       assert !d.upToDate

       c.timestamp = oldts
     }

    @Test
    void testLeafOnlyMissing() {
       d.outputFile.existsOverride = false
       def result = Dependencies.instance.computeOutputGraph([a,b,c,d])

       // now that d is missing, it should not be up to date, because it is a leaf
       assert a.upToDate
       assert b.upToDate
       assert c.upToDate
       assert !d.upToDate
    }

    @Test
    void testInternalOnlyMissing() {
       c.outputFile.existsOverride = false
       c.cleaned = true

       def result = Dependencies.instance.computeOutputGraph([a,b,c,d])

       // even though c is missing, it is still up to date, because it is not a leaf
       assert a.upToDate
       assert b.upToDate
       assert c.upToDate
       assert d.upToDate
    }

    @Test
    void testLeafAndInternalMissing() {
       c.outputFile.existsOverride = false
       d.outputFile.existsOverride = false
       def result = Dependencies.instance.computeOutputGraph([a,b,c,d])

       // now d is missing as well as c, so c becomes out of date too
       assert a.upToDate
       assert b.upToDate
       assert !c.upToDate
       assert !d.upToDate
    }

    @Test
    void testLeafOutOfDateInternalMissing() {
       c.outputFile.existsOverride = false
       d.timestamp = 0
       def result = Dependencies.instance.computeOutputGraph([a,b,c,d])

       // now d is out of date, as well as c missing, so c becomes out of date too
       assert a.upToDate
       assert b.upToDate
       assert !c.upToDate
       assert !d.upToDate
    }

    @Test
    void testFirstOutputMissing() {
       a.outputFile.existsOverride = false
       a.cleaned = true
       c.outputFile.existsOverride = false
       c.cleaned = true

       def result = Dependencies.instance.computeOutputGraph([a,b,c,d])

       // now a is missing, but still up to date because all outputs still exist and up to date
       assert a.upToDate
       assert b.upToDate
       assert c.upToDate
       assert d.upToDate
    }
}
