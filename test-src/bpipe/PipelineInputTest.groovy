package bpipe;

import static org.junit.Assert.*;

import org.junit.Test;

class PipelineInputTest {

    @Test
    public void testPlusOperator() {
        PipelineInput inp = new PipelineInput("foo.txt", [], null)

        println( inp + ".bar")

    }

}
