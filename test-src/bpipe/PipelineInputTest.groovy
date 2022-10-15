package bpipe;

import static org.junit.Assert.*;

import org.junit.Test;

import bpipe.storage.LocalPipelineFile

class PipelineInputTest {

    @Test
    public void testPlusOperator() {
        PipelineInput inp = new PipelineInput(LocalPipelineFile.from(["foo.txt"]), [], new Aliases())

        println( inp + ".bar")

    }

}
