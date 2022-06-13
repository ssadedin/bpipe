package bpipe

import bpipe.executor.CloudExecutor
import bpipe.executor.CommandExecutor
import bpipe.storage.LocalFileSystemStorageLayer
import bpipe.storage.StorageLayer
import groovy.transform.CompileStatic

/**
 * Dependency that provisions the Bpipe Jar file and groovy runner script to the remote server
 * so that it can execute groovy scripts.
 * 
 * @author simon.sadedin
 */
@CompileStatic
class BpipeGroovyRunnerDependency implements CommandDependency {

    @Override
    public void provision(CommandExecutor executor) throws PipelineError {
        if(!(executor instanceof CloudExecutor))
            return

        CloudExecutor cloudExecutor = (CloudExecutor)executor
        String bpipeGroovyScript = Runner.BPIPE_HOME + '/bin/groovy_script'
        List bpipeJarFiles = [
            Runner.BPIPE_HOME + '/build/libs/bpipe-all.jar',
            Runner.BPIPE_HOME + '/lib/bpipe-all.jar',
        ]
        String bpipeJarFile = bpipeJarFiles.find { new File(it).exists() }
        
        List extraLibs = Config.listValue("libs").collect { libPath ->
            if(new File(libPath).exists())
                return libPath

            File relativeToScript = new File(Runner.scriptDirectory, libPath)
            if(relativeToScript.exists())
                return libPath
        }
        .grep {
            it
        }
        
        List filesToTransfer = [
            bpipeGroovyScript,
            bpipeJarFile,
            *extraLibs
        ].collect { new PipelineFile((String)it, new LocalFileSystemStorageLayer())}
        cloudExecutor.transferTo(filesToTransfer)
    }
}
