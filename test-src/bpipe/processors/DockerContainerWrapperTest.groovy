package bpipe.processors

import bpipe.Command
import org.junit.Test

class DockerContainerWrapperTest {

    DockerContainerWrapper underTest = new DockerContainerWrapper()

    Command cmd

    @Test
    void 'Default returns valid docker run command'() {
        cmd = new Command()
        cmd.setCommand('container_cmd')
        cmd.setRawProcessedConfig([container: [type: 'docker', image: 'testImage', options: ['--rm', '--user', '1000']]])
        underTest.transform(cmd, [])

        String defaultWorkDir = new File(".").absoluteFile.parentFile.absolutePath
        String expWorkDir = "-w $defaultWorkDir"
        String expDefaultVolume = "-v $defaultWorkDir:$defaultWorkDir"
        String actualCommand = cmd.shell.join(' ')

        assert actualCommand.contains('docker run')
        assert actualCommand.contains('--rm')
        assert actualCommand.contains('--user 1000')
        assert actualCommand.contains('--entrypoint /usr/bin/env')
        assert actualCommand.contains(expWorkDir)
        assert actualCommand.contains(expDefaultVolume)
        assert actualCommand.contains('testImage')
        assert actualCommand.contains('/bin/bash -e')
    }
}
