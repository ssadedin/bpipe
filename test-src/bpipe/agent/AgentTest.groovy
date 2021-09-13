package bpipe.agent

import static org.junit.Assert.*

import org.junit.Test

import bpipe.cmd.RunPipelineCommand

class AgentTest extends groovy.util.GroovyTestCase {

    RunPipelineCommand rpc = new RunPipelineCommand(['-p','foo=bar','hello.groovy']);
    ConfigObject cfg = new ConfigObject();

    {
        rpc.dir = "/foo/bar/baz"
    }

    Agent agent = new HttpAgent(cfg.tap { allow = new ConfigObject() })
 
    @Test
    public void 'test disallowed pipeline fails validation'() {
      
        agent.allowed.pipelines = ['nothello.groovy']
        
        shouldFail(IllegalArgumentException) {
            agent.validateCommand(rpc)
        }
    }
    
    @Test
    void 'test allowed pipeline passes validation'() {
         
        agent.allowed.pipelines = ['hello.groovy']
        agent.validateCommand(rpc)
    }
    
    @Test
    void 'test disallowed directrory fails validation '() {
        agent.allowed.directories = ['/foo/bar/honk']
        shouldFail(IllegalArgumentException) {
            agent.validateCommand(rpc)
        }
        
        agent.allowed.directories = ['/foo/honk/baz']
        shouldFail(IllegalArgumentException) {
            agent.validateCommand(rpc)
        }
        
        agent.allowed.directories = ['/honk/bar/baz']
        shouldFail(IllegalArgumentException) {
            agent.validateCommand(rpc)
        }
    }

    @Test
    void 'test allowed directrory passes validation '() {
        agent.allowed.directories = ['/foo/bar/baz']
        agent.validateCommand(rpc)
        
        agent.allowed.directories = ['/foo/bar']
        agent.validateCommand(rpc)

        agent.allowed.directories = ['/foo']
        agent.validateCommand(rpc)
    }
}

