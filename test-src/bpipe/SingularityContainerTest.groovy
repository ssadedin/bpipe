package bpipe

import static org.junit.Assert.*

import org.junit.Before
import org.junit.Test

import bpipe.processors.SingularityContainerWrapper

class SingularityContainerTest {
    
    static {
        System.setProperty('bpipe.home', new File('.').absolutePath)
    }
    
    SingularityContainerWrapper scw = new SingularityContainerWrapper()
    
    Command command = new Command(command:'echo "hello world"')
    
    Map config = [
        container: [ 
            type:'singularity', 
            image:'my_test_image',
            execOptions: '--nv'
        ]
    ]
        
    @Before
    void setup() {
        Config.config.script = 'test.groovy'
    }

    @Test
    public void 'containerised shell contains essential components'() {
        
        command.rawProcessedConfig = config
        scw.transform(command, [])
        
        assert command.shell[0] == 'singularity'
        assert command.shell.findIndexOf { it == '-B' }  > 0
        def execIndex = command.shell.findIndexOf { it == 'exec' }
        assert command.shell[execIndex+1] == '--nv'
        assert command.shell[-3].path == 'my_test_image'
    }

    @Test
    public void 'storage is bound using container run command'() {
        
        Config.userConfig = [ filesystems : [
            teststorage: [ type: 'bind', base: '/tmp/foo']
        ]]
        
        command.rawProcessedConfig = config + [container:config.container+[storage:'teststorage']]
        scw.transform(command, [])
        
        assert command.shell[0] == 'singularity'
        assert command.shell.findIndexOf { it == '-B' }  > 0
        def execIndex = command.shell.findIndexOf { it == 'exec' }
        assert command.shell[execIndex+1] == '--nv'
        assert command.shell[-3].path == 'my_test_image'
        
        def binds = command.shell.findIndexValues { it == '-B' }.collect { command.shell[it+1] }
        assert binds.size() == 2
        assert binds.findIndexOf { it == '/tmp/foo' } >= 0
        assert binds.contains('/tmp/foo')
        
    }
}
