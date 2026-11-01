package bpipe.staging

import java.nio.file.Files
import java.nio.file.Path

import org.junit.After
import org.junit.Before
import org.junit.Test

import bpipe.PipelineError
import bpipe.layers.CommandLayer
import bpipe.layers.CommandLayers
import bpipe.PipelineFile
import bpipe.storage.LocalFileSystemStorageLayer

class StagingTest {

    File tmp

    File data

    File scratch

    DirectoryStagingArea area

    @Before
    void setup() {
        tmp = Files.createTempDirectory('bpipe-staging-test').toFile().canonicalFile
        data = new File(tmp, 'data')
        scratch = new File(tmp, 'scratch')
        data.mkdirs()
        scratch.mkdirs()
        area = (DirectoryStagingArea)StagingAreas.create('scratch', [scratch: [type: 'directory', path: scratch.path]])
    }

    @After
    void tearDown() {
        tmp.deleteDir()
    }

    PipelineFile file(String name, String content=null) {
        File f = new File(data, name)
        if(content != null)
            f.text = content
        return new PipelineFile(f.path, new LocalFileSystemStorageLayer())
    }

    // ---------- configuration and path mapping ----------

    @Test
    void 'staged paths mirror the canonical path under a per-command directory'() {
        String stageDir = area.stageDir('1234', '7')
        assert stageDir == "$scratch.path/bpipe-${System.getProperty('user.name')}-1234-7"

        assert area.stagedInputPath(stageDir, new File('/data/x/../y/test.bam').toPath()) == "$stageDir/data/y/test.bam"
        assert area.stagedOutputPath(stageDir, new File('/out/test.bam').toPath()) == "$stageDir/out/test.bam"
    }

    @Test
    void 'area path is made absolute'() {
        DirectoryStagingArea relative = (DirectoryStagingArea)StagingAreas.create('s', [s: [path: 'some/scratch']])
        assert new File(relative.path).isAbsolute()
        assert relative.path.endsWith('some/scratch')
    }

    @Test
    void 'configuration errors are reported'() {
        assertError('no stagingAreas block') { StagingAreas.create('scratch', null) }
        assertError('no entry') { StagingAreas.create('nope', [scratch: [path: '/x']]) }
        assertError('not supported') { StagingAreas.create('s', [s: [type: 'teleport', path: '/x']]) }
        assertError('must have a path') { StagingAreas.create('s', [s: [type: 'directory']]) }
    }

    @Test
    void 'companion extensions are configurable'() {
        StagingArea a = StagingAreas.create('s', [s: [path: '/x', companions: ['.foo']]])
        assert a.companions == ['.foo']
        assert area.companions == StagingArea.DEFAULT_COMPANIONS
    }

    void assertError(String expected, Closure c) {
        try {
            c()
            assert false : "Expected error containing '$expected'"
        }
        catch(PipelineError e) {
            assert e.message.contains(expected)
        }
    }

    // ---------- path rewriting ----------

    String rewrite(String text) {
        StagingPathRewriter.rewrite(text, ['x.bam': 'S/x.bam', 'x.bam.bai': 'S/x.bam.bai', '/d/x.bam': 'S/d/x.bam', 'out.txt': 'S/out.txt']).text
    }

    @Test
    void 'whole path tokens are rewritten'() {
        assert rewrite('samtools view x.bam') == 'samtools view S/x.bam'
        assert rewrite('x.bam') == 'S/x.bam'
        assert rewrite('cat x.bam x.bam') == 'cat S/x.bam S/x.bam'
        assert rewrite('picard I=x.bam O=out.txt') == 'picard I=S/x.bam O=S/out.txt'
        assert rewrite('cat x.bam >out.txt') == 'cat S/x.bam >S/out.txt'
        assert rewrite("cat 'x.bam' \"x.bam\"") == "cat 'S/x.bam' \"S/x.bam\""
        assert rewrite('a --in=x.bam,x.bam.bai') == 'a --in=S/x.bam,S/x.bam.bai'
        assert rewrite('echo $(cat x.bam)|wc') == 'echo $(cat S/x.bam)|wc'
        assert rewrite('cat /d/x.bam') == 'cat S/d/x.bam'
        assert rewrite('cat x.bam\ncat out.txt\n') == 'cat S/x.bam\ncat S/out.txt\n'
    }

    @Test
    void 'partial and derived paths are not rewritten'() {
        assert rewrite('cat x.bam.bai') == 'cat S/x.bam.bai'
        assert rewrite('tool --prefix x') == 'tool --prefix x'
        assert rewrite('cat x.bam.tmp') == 'cat x.bam.tmp'
        assert rewrite('cat foo_x.bam') == 'cat foo_x.bam'
        assert rewrite('cat /other/x.bam') == 'cat /other/x.bam'
        assert rewrite('cat out.txt.gz') == 'cat out.txt.gz'
    }

    @Test
    void 'replaced paths are reported and replacements are not rewritten again'() {
        StagingPathRewriter.Result r = StagingPathRewriter.rewrite('cat x.bam y', ['x.bam': 'y', 'y': 'z'])
        assert r.text == 'cat y z'
        assert r.replaced == ['x.bam', 'y'] as Set

        assert StagingPathRewriter.rewrite('cat a', [:]).text == 'cat a'
    }

    @Test
    void 'regex characters in paths are treated literally'() {
        assert StagingPathRewriter.rewrite('cat a+b[1].txt a.b', ['a+b[1].txt': 'S']).text == 'cat S a.b'
    }

    // ---------- companions ----------

    @Test
    void 'companion index files are found next to inputs'() {
        ['x.bam', 'x.bam.bai', 'x.bai', 'x.fastq.gz', 'x.sorted.bam', 'ref.fa', 'ref.fa.fai', 'ref.dict', 'v.vcf.gz', 'v.vcf.gz.tbi'].each {
            new File(data, it).text = it
        }

        assert names(Companions.find(new File(data, 'x.bam').toPath(), StagingArea.DEFAULT_COMPANIONS)) == ['x.bam.bai', 'x.bai'] as Set
        assert names(Companions.find(new File(data, 'ref.fa').toPath(), StagingArea.DEFAULT_COMPANIONS)) == ['ref.fa.fai', 'ref.dict'] as Set
        assert names(Companions.find(new File(data, 'v.vcf.gz').toPath(), StagingArea.DEFAULT_COMPANIONS)) == ['v.vcf.gz.tbi'] as Set
        assert Companions.find(new File(data, 'x.fastq.gz').toPath(), StagingArea.DEFAULT_COMPANIONS).isEmpty()
    }

    Set<String> names(List<Path> paths) {
        paths.collect { it.fileName.toString() } as Set
    }

    // ---------- planning ----------

    @Test
    void 'only referenced inputs and their companions are staged'() {
        PipelineFile bam = file('x.bam', 'bam')
        file('x.bam.bai', 'bai')
        PipelineFile unused = file('unused.txt', 'unused')
        PipelineFile out = file('x.out.txt')

        String stageDir = area.stageDir('1', '2')
        StagingPlanner.StagedCommand staged = StagingPlanner.stage(area, stageDir, "cat $bam > $out", [bam, unused], [out])

        assert staged.plan.inputs.keySet() == [bam.path, "${bam.path}.bai".toString()] as Set
        assert staged.plan.inputs[bam.path] == stageDir + bam.path
        assert staged.plan.outputs == [(out.path): stageDir + out.path]
        assert staged.command == "cat ${stageDir}${bam.path} > ${stageDir}${out.path}"
    }

    @Test
    void 'relative paths are rewritten to staged canonical paths'() {
        // A relative path resolves against the working directory
        File cwdFile = new File('build/staging-relative-test.txt')
        cwdFile.parentFile.mkdirs()
        cwdFile.text = 'hello'
        try {
            PipelineFile rel = new PipelineFile('build/staging-relative-test.txt', new LocalFileSystemStorageLayer())
            String stageDir = area.stageDir('1', '2')
            StagingPlanner.StagedCommand staged = StagingPlanner.stage(area, stageDir, "cat $rel", [rel], [])
            assert staged.plan.inputs == [(cwdFile.canonicalPath): stageDir + cwdFile.canonicalPath]
            assert staged.command == "cat $stageDir$cwdFile.canonicalPath"
        }
        finally {
            cwdFile.delete()
        }
    }

    @Test
    void 'missing inputs and commands without staged files are left unchanged'() {
        PipelineFile missing = file('missing.txt')
        StagingPlanner.StagedCommand staged = StagingPlanner.stage(area, area.stageDir('1', '2'), "cat $missing", [missing], [])
        assert staged.plan.isEmpty()
        assert staged.command == "cat $missing"
        
        CommandLayers layers = new CommandLayers()
        area.addHooks(staged.plan, layers)
        assert layers.isEmpty() : 'no hooks should be added when nothing is staged'
    }
    
    @Test
    void 'directory staging runs in the job layer'() {
        PipelineFile input = file('in.txt', 'hello')
        StagingPlanner.StagedCommand staged = StagingPlanner.stage(area, area.stageDir('1', '2'), "cat $input", [input], [])
        CommandLayers layers = new CommandLayers()
        area.addHooks(staged.plan, layers)
        assert layers.getHooks(CommandLayer.JOB)*.name == ['staging:scratch']
        assert layers.getHooks(CommandLayer.INNER).isEmpty()
    }

    // ---------- execution of the wrapped command ----------

    Map runStaged(String command, List<PipelineFile> inputs, List<PipelineFile> outputs, List<String> shell) {
        String stageDir = area.stageDir('1', '2')
        StagingPlanner.StagedCommand staged = StagingPlanner.stage(area, stageDir, command, inputs, outputs)
        CommandLayers layers = new CommandLayers()
        area.addHooks(staged.plan, layers)
        Process p = new ProcessBuilder(shell).redirectErrorStream(true).start()
        p.outputStream.withWriter { it << layers.render(staged.command) }
        String output = p.inputStream.text
        int exit = p.waitFor()
        return [exit: exit, output: output, stageDir: new File(stageDir), plan: staged.plan]
    }

    static final List<List<String>> SHELLS = [['bash'], ['bash', '-e']]

    @Test
    void 'successful command reads staged input and returns output'() {
        for(List<String> shell in SHELLS) {
            PipelineFile input = file('in.txt', 'hello')
            PipelineFile output = file('out.txt')

            Map r = runStaged("echo \"reading from $input\"; cat $input > $output", [input], [output], shell)

            assert r.exit == 0 : r.output
            assert r.output.contains("reading from ${r.stageDir}") : 'command should see staged path'
            assert new File(output.path).text == 'hello'
            assert !r.stageDir.exists() : 'stage directory should be removed'
            new File(output.path).delete()
        }
    }

    @Test
    void 'failed command keeps exit code and discards outputs'() {
        // Without errexit a failing statement does not end the command, so 'false' only fails under bash -e
        List<List> cases = [[['bash'], 'exit 3'], [['bash', '-e'], 'exit 3'], [['bash', '-e'], 'false']]
        for(List c in cases) {
            List<String> shell = (List<String>)c[0]
            String failure = (String)c[1]
            PipelineFile input = file('in.txt', 'hello')
            PipelineFile output = file('out.txt')

            Map r = runStaged("cat $input > $output\n$failure\necho should not get here", [input], [output], shell)

            int expected = failure == 'exit 3' ? 3 : 1
            assert r.exit == expected : "$shell / $failure: $r.output"
            assert !new File(output.path).exists() : "$shell / $failure: output should not be returned"
            assert !r.stageDir.exists() : "$shell / $failure: stage directory should be removed"
        }
    }

    @Test
    void 'stage in failure is reported'() {
        PipelineFile input = file('in.txt', 'hello')
        PipelineFile output = file('out.txt')
        
        // A file where the stage directory must be created makes staging impossible
        new File(area.stageDir('1', '2')).text = 'blocker'
        
        Map r = runStaged("cat $input > $output", [input], [output], ['bash'])
        
        assert r.exit != 0
        assert r.output.contains('Bpipe staging: failed')
        assert !new File(output.path).exists()
    }
    
    @Test
    void 'outputs written directly to canonical location are left in place'() {
        PipelineFile input = file('in.txt', 'hello')
        PipelineFile output = file('out.txt')

        // Command writes the output via a derived path that is not rewritten
        String dir = new File(output.path).parent
        Map r = runStaged("cat $input > \$(echo $dir)/out.txt", [input], [output], ['bash', '-e'])

        assert r.exit == 0 : r.output
        assert new File(output.path).text == 'hello'
    }

    @Test
    void 'side effect outputs produced next to a staged output are returned'() {
        PipelineFile input = file('in.txt', 'hello')
        PipelineFile output = file('out.txt')
        PipelineFile index = file('out.txt.idx')

        // The command only names out.txt, but creates out.txt.idx alongside it
        Map r = runStaged("cat $input > $output; echo idx > ${output}.idx", [input], [output, index], ['bash', '-e'])

        // ${output}.idx is not a whole token for out.txt, but out.txt.idx is itself a declared output
        assert r.exit == 0 : r.output
        assert new File(output.path).text == 'hello'
        assert new File(index.path).text.trim() == 'idx'
    }
}
