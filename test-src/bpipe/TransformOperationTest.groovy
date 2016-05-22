package bpipe

import org.junit.Test;

class TransformOperationTest {
    
    TransformOperation op
    
    Branch branch = new Branch(name:"testbranch")
    
    PipelineContext ctx = new PipelineContext(null, [],[], branch)
    
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
}