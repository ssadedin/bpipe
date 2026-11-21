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

    def savedUserConfig

    @Before
    void setUp() {
        savedUserConfig = Config.userConfig
        testDir.deleteDir()
        stagedA.mkdirs()
        stagedB.mkdirs()
    }

    @After
    void tearDown() {
        Config.userConfig = savedUserConfig
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

    String canon(String name) {
        new File(out(name)).canonicalPath
    }

    @Test
    void testIndexesCopiedWithOutput() {
        stage(stagedA, out('x.bam'), 'bam')
        stage(stagedA, out('x.bam.bai'), 'bai')
        stage(stagedA, out('x.csi'), 'csi')
        stage(stagedA, out('x.bam.flagstat'))  // not an index: must not be copied
        stage(stagedA, out('y.vcf.gz'))
        stage(stagedA, out('y.vcf.gz.tbi'))
        StagedOutputs.configure(stagedA.path)

        StagingPlan plan = StagedOutputs.plan('test', outputs('x.bam', 'y.vcf.gz'))
        assertNotNull plan

        // Index files are not outputs
        assertEquals 2, plan.files.size()

        assertEquals([canon('x.bam.bai'), canon('x.csi')] as Set, plan.indexes[canon('x.bam')].keySet())
        assertEquals([canon('y.vcf.gz.tbi')], plan.indexes[canon('y.vcf.gz')].keySet() as List)
        assertEquals 3, plan.indexFiles.size()

        String script = plan.toScript('make stuff')

        // Index copied after the file it indexes
        assertTrue script.indexOf("x.bam.bai'") > script.indexOf("x.bam' ")

        File scriptFile = new File(testDir, 'copy.sh')
        scriptFile.text = script
        assertEquals 0, ['bash', scriptFile.path].execute().waitFor()
        assertEquals 'bai', new File(out('x.bam.bai')).text
        assertEquals 'csi', new File(out('x.csi')).text
        assertTrue new File(out('y.vcf.gz.tbi')).exists()
        assertFalse new File(out('x.bam.flagstat')).exists()
    }

    @Test
    void testIndexOnlyFromSameStagingDir() {
        stage(stagedA, out('x.bam'))
        stage(stagedB, out('x.bam.bai'))
        StagedOutputs.configure("$stagedA.path,$stagedB.path")

        StagingPlan plan = StagedOutputs.plan('test', outputs('x.bam'))
        assertNotNull plan
        assertTrue plan.indexFiles.isEmpty()
    }

    @Test
    void testDeclaredIndexOutputNotDuplicated() {
        stage(stagedA, out('x.bam'))
        stage(stagedA, out('x.bam.bai'))
        StagedOutputs.configure(stagedA.path)

        StagingPlan plan = StagedOutputs.plan('test', outputs('x.bam', 'x.bam.bai'))
        assertEquals 2, plan.files.size()
        assertTrue plan.indexFiles.isEmpty()
        assertEquals 1, plan.toScript('x').readLines().count { it.startsWith('cp ') && it.endsWith("x.bam.bai'") }
    }

    @Test
    void testMissingIndexIsFine() {
        stage(stagedA, out('x.bam'))
        StagedOutputs.configure(stagedA.path)
        StagingPlan plan = StagedOutputs.plan('test', outputs('x.bam'))
        assertNotNull plan
        assertTrue plan.indexFiles.isEmpty()
    }

    @Test
    void testIndexConfigMergedWithDefaults() {
        Config.userConfig = new ConfigSlurper().parse("""
            staging {
                indexes = [
                    '.foo': ['.fidx'],
                    bam: [],
                    cram: 'crai2'
                ]
            }
        """)

        Map<String,List<String>> exts = StagedOutputs.getIndexExtensions()
        assertEquals(['fidx'], exts.foo)
        assertEquals([], exts.bam)
        assertEquals(['crai2'], exts.cram)
        assertEquals(['tbi', 'csi'], exts['vcf.gz'])

        stage(stagedA, out('x.bam'))
        stage(stagedA, out('x.bam.bai'))
        stage(stagedA, out('z.foo'))
        stage(stagedA, out('z.fidx'))
        StagedOutputs.configure(stagedA.path)

        StagingPlan plan = StagedOutputs.plan('test', outputs('x.bam', 'z.foo'))
        assertEquals([canon('z.fidx')], plan.indexes.values()*.keySet().flatten())
    }

    @Test(expected=PipelineError)
    void testInvalidIndexConfig() {
        Config.userConfig = new ConfigSlurper().parse("staging { indexes = ['bai'] }")
        StagedOutputs.getIndexExtensions()
    }

    @Test
    void testDirectoryOutputHasNoIndexes() {
        stage(stagedA, out('d.bam/x.txt'))
        stage(stagedA, out('d.bam.bai'))
        StagedOutputs.configure(stagedA.path)
        StagingPlan plan = StagedOutputs.plan('test', outputs('d.bam'))
        assertTrue plan.indexFiles.isEmpty()
    }
}
