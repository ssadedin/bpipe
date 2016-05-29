package bpipe

import org.junit.Before;
import org.junit.Test;

class TransformOperationTest {
    
    TransformOperation op
    
    Branch branch = new Branch(name:"testbranch")
    
    PipelineStage stage = new PipelineStage(null)
    
    PipelineContext ctx = new PipelineContext(null, [stage],[], branch)
    
    Pipeline pipeline = new Pipeline()
    
    @Before
    void setup() {
        Pipeline.currentRuntimePipeline.set(pipeline)
        stage.context = ctx
        ctx.aliases = new Aliases()
    }
    
    def txop(List inputs, List exts) {
        ctx.@input = inputs
        op = new TransformOperation(ctx, exts, null) 
        return op.computeOutputFiles(null, "stage")        
    }
    
    @Test
    void testOneToOne() {
        def out = txop(["foo.txt"], ["tsv"])
        assert out == ["foo.tsv"]
    }
    
    @Test
    void testOneToMany() {
        def out = txop(["foo.txt"], ["tsv","bam"])
        assert out == ["foo.tsv","foo.bam"]        
    }
    
    @Test
    void testTwoToTwo() {
        def out = txop(["foo.txt","foo.csv"], ["tsv","bam"])
        assert out == ["foo.tsv","foo.bam"]        
    }
    
    @Test
    void testMultiInputsOneOutput() {
        def out = txop(["foo.txt","bar.txt"], ["tsv"])
        
        // Note that this behavior is inconsistent with what you will observe
        // from bpipe if you do the transform foo.txt,bar.txt => tsv, but that
        // is because the output exts get expanded by a separate method prior to the
        // one we are testing here.
        assert out == ["foo.tsv"]
    }
    
    /**
     * test1.txt test2.txt => test1.foo.txt, test2.foo.txt via 
     * transform("*.txt") to (".csv") 
     */
    @Test
    void testNumberedInputs() {
        
        def inputs = ["test1.txt","test2.txt","test.xml"]
        
        ctx.@input = inputs
        op = new TransformOperation(ctx, ["*.txt",".xml"], null)         
        
        op.to(".zip",".tsv") {
            def i1 = input1.txt
            def i2 = input2.txt
            println "input1 = $i1"
            println "input2 = $i2"
            assert i1 == "test1.txt"
            assert i2 == "test2.txt"
        }
    }
    
    @Test
    void testOneToOneMultiInputs() {
        
        def inputs = ["test1.txt","test2.txt"]
        
        ctx.@input = inputs
        op = new TransformOperation(ctx, [".txt"], null)         
        
        op.to(".tsv") {
            def i1 = input1.txt
            println "input1 = $i1"
            assert i1 == "test1.txt"
            
            def o1 = output1.tsv
            println "o1 = $o1"
        }
    } 
}