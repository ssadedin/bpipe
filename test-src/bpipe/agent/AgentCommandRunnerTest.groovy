package bpipe.agent

import org.junit.Test

import bpipe.ExecutedProcess
import bpipe.cmd.RunPipelineCommand
import bpipe.worx.WorxConnection
import groovy.json.JsonSlurper

class AgentCommandRunnerTest extends GroovyTestCase {

    /**
     * Build a WorxConnection stub that captures the last JSON payload sent to it.
     * Results are appended to the supplied list so the closure can write back
     * even though closures capture by reference.
     */
    private WorxConnection stubWorx(List<Map> captured) {
        return new WorxConnection() {
            @Override void sendJson(String path, String json) {
                captured << (new JsonSlurper().parseText(json) as Map)
            }
            @Override Object readResponse() { null }
            @Override void close() {}
        }
    }

    /**
     * Build a RunPipelineCommand that writes the given text to its output writer
     * and returns the given exit code, without ever launching a real bpipe process.
     */
    private RunPipelineCommand stubCommand(String output, int exitCode) {
        return new RunPipelineCommand(['test.groovy']) {
            {
                // Point at a temp dir that has no .bpipe/lock, so
                // checkRunningPipelineLock() exits early without blocking.
                dir = System.getProperty('java.io.tmpdir')
            }
            @Override
            void run(Writer out) {
                out.write(output)
                out.flush()
                result = new ExecutedProcess(exitValue: exitCode)
            }
        }
    }

    @Test
    void 'test output is captured when command succeeds'() {
        List<Map> captured = []
        AgentCommandRunner runner = new AgentCommandRunner(
            stubWorx(captured), 1L, stubCommand('hello from pipeline\n', 0), 'both')

        runner.run()

        assert captured.size() == 1
        assert captured[0].status == 'ok'
        assert captured[0].output?.contains('hello from pipeline')
    }

    /**
     * Regression test for the outputMode shadowing bug:
     *   String outputMode = isRunCommand ? outputMode : 'reply'
     * Before the fix the RHS 'outputMode' resolved to the uninitialized local
     * variable (null) rather than this.outputMode, causing out == null inside
     * TeeWriter and a NullPointerException in the TextDumper thread.
     */
    @Test
    void 'test no NPE when outputMode is both and command exits quickly with failure'() {
        List<Map> captured = []
        AgentCommandRunner runner = new AgentCommandRunner(
            stubWorx(captured), 2L, stubCommand('error output\n', 1), 'both')

        // Should not throw; before the fix this produced a NPE in TextDumper
        runner.run()

        assert captured.size() == 1
        // The agent runner itself succeeded in dispatching the result
        assert captured[0].status == 'ok'
        // And the output was actually captured despite the fast exit
        assert captured[0].output?.contains('error output')
    }

    @Test
    void 'test output mode stream does not NPE'() {
        List<Map> captured = []
        AgentCommandRunner runner = new AgentCommandRunner(
            stubWorx(captured), 3L, stubCommand('stream output\n', 0), 'stream')

        runner.run()

        assert captured.size() >= 1
        assert captured.last().status == 'ok'
    }
}
