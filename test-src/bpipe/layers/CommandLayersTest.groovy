package bpipe.layers

import org.junit.Test

class CommandLayersTest {

    static final List<List<String>> SHELLS = [['bash'], ['bash', '-e'], ['sh'], ['sh', '-e']]

    /**
     * Run the rendered text under the given shell, the way executors feed commands to a shell
     */
    Map run(CommandLayers layers, String command, List<String> shell) {
        Process p = new ProcessBuilder(shell).redirectErrorStream(true).start()
        p.outputStream.withWriter { it << layers.render(command) }
        String output = p.inputStream.text
        return [exit: p.waitFor(), output: output, lines: output.readLines()]
    }

    CommandLayers hooks(Map<CommandLayer, List<List<String>>> spec) {
        CommandLayers layers = new CommandLayers()
        spec.each { CommandLayer layer, List<List<String>> layerHooks ->
            layerHooks.eachWithIndex { List<String> h, int i ->
                layers.add(layer, new ShellHook(name: "$layer-$i", pre: h[0], post: h[1]))
            }
        }
        return layers
    }

    @Test
    void 'command without hooks is unchanged'() {
        CommandLayers layers = new CommandLayers()
        assert layers.isEmpty()
        assert layers.render('echo hello') == 'echo hello'
        assert layers.render('echo "a"\necho b\n') == 'echo "a"\necho b\n'
    }

    @Test
    void 'pre hooks run in order, post hooks in reverse, inner layer inside job layer'() {
        CommandLayers layers = hooks(
            (CommandLayer.JOB): [['echo job-pre-1', 'echo job-post-1'], ['echo job-pre-2', 'echo job-post-2']],
            (CommandLayer.INNER): [['echo inner-pre', 'echo inner-post']]
        )
        for(List<String> shell in SHELLS) {
            Map r = run(layers, 'echo command', shell)
            assert r.exit == 0 : "$shell: $r.output"
            assert r.lines == ['job-pre-1', 'job-pre-2', 'inner-pre', 'command', 'inner-post', 'job-post-2', 'job-post-1'] : shell
        }
    }

    @Test
    void 'exit status of the command is preserved through all layers'() {
        CommandLayers layers = hooks(
            (CommandLayer.JOB): [['echo pre', 'echo "job post sees $__bpipe_rc"']],
            (CommandLayer.INNER): [[null, 'echo "inner post sees $__bpipe_rc"']]
        )
        for(List<String> shell in SHELLS) {
            for(String failure in ['exit 3', '(exit 5)'] + (shell.contains('-e') ? ['false'] : [])) {
                Map r = run(layers, "echo command\n$failure\necho after", shell)
                int expected = failure == 'exit 3' ? 3 : (failure == 'false' ? 1 : 5)

                // Without errexit, a failing statement that isn't 'exit' does not end the command
                if(failure == '(exit 5)' && !shell.contains('-e')) {
                    assert r.exit == 0 : "$shell / $failure: $r.output"
                    continue
                }
                assert r.exit == expected : "$shell / $failure: $r.output"
                assert r.lines.contains("inner post sees $expected".toString()) : "$shell / $failure: $r.output"
                assert r.lines.contains("job post sees $expected".toString()) : "$shell / $failure: $r.output"
                assert !r.lines.contains('after') : "$shell / $failure"
            }
        }
    }

    @Test
    void 'post hook can fail the command'() {
        CommandLayers layers = hooks((CommandLayer.JOB): [[null, 'echo "checking"; __bpipe_rc=7']])
        for(List<String> shell in SHELLS) {
            Map r = run(layers, 'echo command', shell)
            assert r.exit == 7 : "$shell: $r.output"
        }
    }

    @Test
    void 'failing pre hook skips the command but still runs post hooks'() {
        CommandLayers layers = hooks((CommandLayer.JOB): [['echo pre; exit 4', 'echo post']])
        for(List<String> shell in SHELLS) {
            Map r = run(layers, 'echo command', shell)
            assert r.exit == 4 : "$shell: $r.output"
            assert r.lines == ['pre', 'post'] : shell
        }
    }

    @Test
    void 'a failing statement in a post hook does not stop later post hooks under errexit'() {
        CommandLayers layers = hooks((CommandLayer.JOB): [[null, 'echo first'], [null, 'false\necho second-continues']])
        for(List<String> shell in SHELLS) {
            Map r = run(layers, 'echo command', shell)
            assert r.exit == 0 : "$shell: $r.output"
            assert r.lines == ['command', 'second-continues', 'first'] : shell
        }
    }

    @Test
    void 'status is recorded after post hooks by a following statement in the same shell'() {
        // As executors like the AWS one do: run the command, then write $? in the same shell
        CommandLayers layers = hooks((CommandLayer.JOB): [[null, 'echo post']])
        String rendered = layers.render('echo command; exit 2')
        Process p = new ProcessBuilder(['bash']).redirectErrorStream(true).start()
        p.outputStream.withWriter { it << rendered + 'echo "recorded $?"\n' }
        List<String> lines = p.inputStream.text.readLines()
        p.waitFor()
        assert lines == ['command', 'post', 'recorded 2']
    }

    @Test
    void 'heredocs in hooks and command are preserved'() {
        CommandLayers layers = hooks((CommandLayer.JOB): [['cat <<EOF\npre-doc\nEOF', 'cat <<EOF\npost-doc\nEOF']])
        Map r = run(layers, "cat <<'EOF'\ncommand-doc \$HOME\nEOF", ['bash', '-e'])
        assert r.exit == 0 : r.output
        assert r.lines == ['pre-doc', 'command-doc $HOME', 'post-doc']
    }
}
