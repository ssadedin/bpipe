package bpipe

import org.junit.Before;
import org.junit.BeforeClass
import org.junit.Test;

import bpipe.storage.LocalPipelineFile
import groovy.transform.CompileStatic

class TestPipelineContext extends PipelineContext {

    public TestPipelineContext(Binding extraBinding, List<PipelineStage> pipelineStages,
    Set<Closure> pipelineJoiners, Branch branch) {
        super(extraBinding, pipelineStages, pipelineJoiners, branch);
    }

    @Override
    public Object produceImpl(Object rawOut, Closure body, boolean explicit) {
        return null
    }

}

class TransformOperationTest {
    
    TransformOperation op
    
    Branch branch = new Branch(name:"testbranch")
    
    PipelineStage stage = new PipelineStage(null)
    
    PipelineContext ctx = new PipelineContext(null, [stage],Collections.emptySet(), branch)  
    
    Pipeline pipeline = new Pipeline()
    
    @BeforeClass
    static void setConfig() {
        Config.userConfig = new ConfigObject()
        Config.userConfig.storage = 'local'
        System.setProperty('bpipe.home', new File('.').absolutePath)
    }
    
    @Before
    void setup() {
        Pipeline.currentRuntimePipeline.set(pipeline)

        ctx.trackedOutputs = [ 1:  new Command(command: 'test command', cfg: [storage:'local'])]
        stage.context = ctx
        ctx.aliases = new Aliases()
    }
    
    @CompileStatic
    def txop(List<String> inputs, List exts) {
        ctx.@input = (List<PipelineFile>)inputs.collect { new LocalPipelineFile(it) }
        op = new TransformOperation(ctx, exts, null) 
        
        return op.computeOutputFiles(null).collect { it.name }
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
        
        def inputs = ["test1.txt","test2.txt","test.xml"].collect { new LocalPipelineFile(it) }
        
        ctx.@input = inputs
        op = new TransformOperation(ctx, ["*.txt",".xml"], null)         
        
        op.to(".zip",".tsv") {
            ctx.trackedOutputs[1] = new Command(cfg:[:])
            def i1 = input1.txt
            def i2 = input2.txt
            println "input1 = $i1"
            println "input2 = $i2"
            assert i1.toString() == "test1.txt"
            assert i2.toString() == "test2.txt"
        }
    }
    
    @Test
    void testOneToOneMultiInputs() {
        
        def inputs = ["test1.txt","test2.txt"].collect { new LocalPipelineFile(it) }
        
        ctx.setRawInput(inputs)
        op = new TransformOperation(ctx, [".txt"], null)         
        
        op.to(".tsv") {
            
            ctx.trackedOutputs[1] = new Command(cfg:[:])
            
            def i1 = input1.txt
            println "input1 = $i1"
            assert i1.toString() == "test1.txt"
            
            def o1 = output1.tsv
            println "o1 = $o1"
        }
    }
        
        
      @Test
      void 'regular expression substitutions should be replaced'() {
        
        def inputs = ["foo.txt", "test_R1.txt"].collect { new LocalPipelineFile(it) }
        
        ctx.@input = inputs
        op = new TransformOperation(ctx, ["(.*)_R1.txt"], null)         
        
        op.to("\$1.xml") {
            ctx.trackedOutputs[1] = new Command(cfg:[:])
            def i1 = input1.txt
            assert i1.toString() == "test_R1.txt"
            
            def o1 = output.xml
            
            assert o1.toString() == "test.xml"
        }
    } 
    
    @Test
    void multi_files_match_regex() {
        def inputs = ["test_1.fastq.gz", "test_2.fastq.gz"].collect { new LocalPipelineFile(it) }
        
        ctx.@input = inputs
        op = new TransformOperation(ctx, [~"(.*).fastq.gz"], null)         
        
        op.to("\$1.xml") {
            ctx.trackedOutputs[1] = new Command(cfg:[:])
            def i1 = input1.gz
            assert i1.toString() == "test_1.fastq.gz"

            def i2 = input2.gz
            assert i2.toString() == "test_2.fastq.gz"
            
            def o1 = output1.xml
            assert o1.toString() == "test_1.xml"
            
            def o2 = output2
            assert o2.toString() == "test_2.xml"
             
        }
        
    }
}