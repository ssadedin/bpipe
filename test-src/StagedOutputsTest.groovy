package bpipe

import static org.junit.Assert.*

import org.junit.After
import org.junit.Before
import org.junit.Test

import bpipe.storage.LocalPipelineFile

class StagedOutputsTest {

    // Staging works on paths relative to the run directory, so the
    // test files must live inside it
    File testDir = new File('build/tmp/staged_outputs_test')

    File stagedA = new File(testDir, 'staged_a')

    File stagedB = new File(testDir, 'staged_b')

    @Before
    void setUp() {
        testDir.deleteDir()
        stagedA.mkdirs()
        stagedB.mkdirs()
    }

    @After
    void tearDown() {
        Config.config.remove('stagedDirs')
        testDir.deleteDir()
    }

    /**
     * Create a staged file in the given staging dir for an output at the given path
     * (relative to the run directory)
     */
    void stage(File stagingDir, String path, String content='staged') {
        File f = new File(stagingDir, path)
        f.parentFile.mkdirs()
        f.text = content
    }

    String out(String name) {
        new File(testDir, 'out/' + name).path
    }

    List<PipelineFile> outputs(String... names) {
        names.collect { new LocalPipelineFile(out(it)) }
    }

    @Test
    void testDisabledByDefault() {
        assertFalse StagedOutputs.isEnabled()
        assertNull StagedOutputs.plan('test', outputs('a.txt'))
    }

    @Test
    void testAllOutputsStaged() {
        stage(stagedA, out('a.txt'))
        stage(stagedA, out('b.txt'))
        StagedOutputs.configure(stagedA.path)

        StagingPlan plan = StagedOutputs.plan('test', outputs('a.txt', 'b.txt'))
        assertNotNull plan
        assertEquals 2, plan.files.size()
        assertEquals([stagedA.canonicalPath], plan.stagedDirs)
        assertEquals stagedA.canonicalPath, plan.sourceFor(outputs('a.txt')[0])
    }

    @Test
    void testPartiallyStagedIsNotPlanned() {
        stage(stagedA, out('a.txt'))
        StagedOutputs.configure(stagedA.path)
        assertNull StagedOutputs.plan('test', outputs('a.txt', 'b.txt'))
    }

    @Test
    void testFirstStagingDirWins() {
        stage(stagedA, out('a.txt'))
        stage(stagedB, out('a.txt'))
        stage(stagedB, out('b.txt'))
        StagedOutputs.configure("$stagedA.path,$stagedB.path")

        StagingPlan plan = StagedOutputs.plan('test', outputs('a.txt', 'b.txt'))
        assertEquals stagedA.canonicalPath, plan.sourceFor(outputs('a.txt')[0])
        assertEquals stagedB.canonicalPath, plan.sourceFor(outputs('b.txt')[0])
    }

    @Test
    void testGlobsAndOutsideRunDirNotStaged() {
        StagedOutputs.configure(stagedA.path)
        assertNull StagedOutputs.findStaged(new LocalPipelineFile(out('*.txt')))
        assertNull StagedOutputs.findStaged(new LocalPipelineFile('/definitely/not/in/run/dir.txt'))
    }

    @Test(expected=PipelineError)
    void testMissingStagingDir() {
        StagedOutputs.configure(new File(testDir, 'nope').path)
    }

    @Test
    void testScript() {
        stage(stagedA, out("it's.txt"))
        stage(stagedA, out('dir/x.txt'))
        StagedOutputs.configure(stagedA.path)

        StagingPlan plan = StagedOutputs.plan('test', outputs("it's.txt", 'dir'))
        String script = plan.toScript("echo hello\necho world")

        assertTrue script.contains("#     echo hello\n#     echo world\n")
        assertFalse script.contains('cp -p')

        // Quoting of embedded single quote
        assertTrue script.contains("it'\\''s.txt")

        // Directory outputs copy contents, not the directory itself
        assertTrue script.contains("/dir/.' '")

        // And the script actually works
        File scriptFile = new File(testDir, 'copy.sh')
        scriptFile.text = script
        assertEquals 0, ['bash', scriptFile.path].execute().waitFor()
        assertEquals 'staged', new File(out("it's.txt")).text
        assertEquals 'staged', new File(out('dir/x.txt')).text
    }

    OutputMetaData stagedFrom(String dir) {
        new OutputMetaData(stagedFrom: dir)
    }

    @Test
    void testStagedOutputOutOfDateWhenStagingDisabled() {
        List<PipelineFile> o = outputs('a.txt')
        assertEquals 1, StagedOutputs.findOutOfDate(o) { stagedFrom(stagedA.canonicalPath) }.size()
        assertEquals 0, StagedOutputs.findOutOfDate(o) { stagedFrom(null) }.size()
        assertEquals 0, StagedOutputs.findOutOfDate(o) { null }.size()
    }

    @Test
    void testRealOutputReplacedWhenFullyStaged() {
        stage(stagedA, out('a.txt'))
        StagedOutputs.configure(stagedA.path)
        List<PipelineFile> o = outputs('a.txt')
        assertEquals 1, StagedOutputs.findOutOfDate(o) { stagedFrom(null) }.size()
        assertEquals 1, StagedOutputs.findOutOfDate(o) { stagedFrom(stagedB.canonicalPath) }.size()
        assertEquals 0, StagedOutputs.findOutOfDate(o) { stagedFrom(stagedA.canonicalPath) }.size()
    }

    @Test
    void testRealOutputKeptWhenPartiallyStaged() {
        stage(stagedA, out('a.txt'))
        StagedOutputs.configure(stagedA.path)
        List<PipelineFile> o = outputs('a.txt', 'b.txt')

        // Real outputs are not replaced, as the command would just run for real again
        assertEquals 0, StagedOutputs.findOutOfDate(o) { stagedFrom(null) }.size()

        // But previously staged outputs must still be regenerated
        assertEquals 2, StagedOutputs.findOutOfDate(o) { stagedFrom(stagedA.canonicalPath) }.size()
    }

    @Test
    void testModifiedStagedFileIsOutOfDate() {
        stage(stagedA, out('a.txt'))
        new File(out('a.txt')).with { parentFile.mkdirs(); text = 'staged' }
        StagedOutputs.configure(stagedA.path)
        List<PipelineFile> o = outputs('a.txt')

        new File(stagedA, out('a.txt')).lastModified = System.currentTimeMillis() - 10000
        assertEquals 0, StagedOutputs.findOutOfDate(o) { stagedFrom(stagedA.canonicalPath) }.size()

        new File(stagedA, out('a.txt')).lastModified = System.currentTimeMillis() + 10000
        assertEquals 1, StagedOutputs.findOutOfDate(o) { stagedFrom(stagedA.canonicalPath) }.size()
    }
}
