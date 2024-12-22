package bpipe.executor

import static org.junit.Assert.*


import org.junit.Before
import org.junit.BeforeClass

import bpipe.Command
import bpipe.CommandId
import bpipe.Utils

//import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import org.junit.Test

class AWSEC2CommandExecutorTest {
    
    Map config  = [
            keypair : System.getProperty('bpipe.test.aws.keypair'),
            accessKey: System.getProperty('bpipe.test.aws.accessKey'),
            accessSecret: System.getProperty('bpipe.test.aws.accessSecret'),
            securityGroup: 'SSH',
            image: 'ami-0acb51609b44c296b',
            user: 'centos',
            region: 'ap-southeast-2'
   ]

    
    @BeforeClass
    static void before() {
        Utils.configureVerboseLogging()
    }

    @Test
    public void 'test acquire instance'() {
        
        if(config.keypair == null) {
            println "Skip because key is null: please set system properties to run this test"
            return
        }
        
        def awse = new AWSEC2CommandExecutor()
        
        awse.acquireInstance(config,'ami-0acb51609b44c296b',"1234")
        
        assert awse.instanceId != null
        
        Thread.sleep(2000)
        
        println "Terminating instance $awse.instanceId"
//        awse.ec2.terminateInstances(new TerminateInstancesRequest([awse.instanceId]))
    }
    
    @Test
    public void 'test start command'() {
        
        if(config.keypair == null) {
            println "Skip because key is null: please set system properties to run this test"
            return
        }
         
        def awse = new AWSEC2CommandExecutor()
        
        Command cmd = new Command(id:CommandId.newId(),command:'pwd', rawProcessedConfig:[:])
        
        StringBuilder output = new StringBuilder()
        awse.start(config, cmd, output, output)
        
        assert awse.ec2 != null
        assert awse.instanceId != null
        
        int exitCode = awse.waitFor()
        
        println "Finished waiting for command: exit code is $exitCode"
        
//        Thread.sleep(20000)
        
        assert exitCode == 0
        
        println "Terminating instance $awse.instanceId"
//        awse.ec2.terminateInstances(new TerminateInstancesRequest([awse.instanceId]))
    }
    
    
    @Test
    public void 'user data calculation'() {
        
        def awse = new AWSEC2CommandExecutor(command: new Command(name: 'test'))
        
        String userData = awse.resolveUserData(walltime:'12:00:00')
        assert userData : "should have user data when walltime is set"
        
        String result = new String(userData.decodeBase64(), 'utf-8')
        assert result.contains("shutdown -h +720")
        
        userData = awse.resolveUserData(walltime:'12:00')
        assert userData : "should have user data when walltime is set"
        result = new String(userData.decodeBase64(), 'utf-8')
        assert result.contains("shutdown -h +12")

        userData = awse.resolveUserData(walltime:'12:00', initScript: 'echo "hello world"')
        assert userData : "should have user data when walltime or initScript is set"
        result = new String(userData.decodeBase64(), 'utf-8')
        assert result.contains("shutdown -h +12")

        userData = awse.resolveUserData(initScript: 'echo "hello world"')
        assert userData : "should have user data when walltime or initScript is set"
        result = new String(userData.decodeBase64(), 'utf-8')
        assert !result.contains("shutdown")
        assert result.contains("hello world")
        
        userData = awse.resolveUserData([:])
        assert !userData : "should not have user data when neither walltime or initScript is set"

    }
}
