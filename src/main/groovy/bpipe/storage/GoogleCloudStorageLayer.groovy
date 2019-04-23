package bpipe.storage

import java.nio.file.Files

import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.Logger

import bpipe.Utils
import bpipe.executor.CommandExecutor
import bpipe.executor.GoogleCloudCommandExecutor

import static bpipe.executor.GoogleCloudCommandExecutor.*

import bpipe.Command
import bpipe.CommandId
import bpipe.Config
import bpipe.ExecutedProcess
import bpipe.PipelineError
import bpipe.PooledExecutor
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.ToString

/**
 * Implements Google Cloud Storage as a storage layer
 * 
 * @author simon.sadedin
 */
@ToString(includeNames=true)
class GoogleCloudStorageLayer extends StorageLayer {
    
    public static final long serialVersionUID = 0L
    
    static Logger log = Logger.getLogger('GoogleCloudStorageLayer')
    
    private String sdkHome
    
    String bucket = getDefaultBucket()
    
    String region
    
    String path

    @Override
    public Path toPath(String path) {
        return Paths.get(URI.create("gs://$bucket/$path"));
    }
    
    @Memoized
    static String getDefaultBucket() {
        'bpipe-' + Utils.sha1(bpipe.Runner.HOSTNAME+'::' + bpipe.Runner.canonicalRunDirectory)
    }

    @Override
    @CompileStatic
    public String getMountCommand(CommandExecutor executor) {
        
//        // Mount commands should executed directly, not sent through the pooled executor mechanism
//        if(executor instanceof PooledExecutor) {
//            executor = executor.executor
//        }
//        
//        init()
//        
//        Map config = [
//            region: region,
//            bucket: bucket
//        ]
//        
//        log.info "Region for Google Cloud is $region"
//        
//        this.makeBucket()
//        
//        log.info "Bucket is $bucket, mounting to path $path"
//        
//        String mountCommand = "mkdir -p $path; mountpoint -q main || { gcsfuse --implicit-dirs --stat-cache-ttl 60s --type-cache-ttl 60s $bucket $path ; }"
//        
//        // Finally mount the storage
////        List<String> sshCommand = ["$sdkHome/bin/gcloud","compute","ssh","--command",mountCommand,gce.instanceId]*.toString()
//        
//        StringBuffer output = new StringBuffer()
//        
//        Command cmd = new Command(command:mountCommand, id:CommandId.newId())
//        Map cfg = [workingDirectory: '', storage:'auto']
//        cmd.setRawProcessedConfig(cfg)
//        executor.start(cfg, cmd, output, output)
//        int exitCode = executor.waitFor()
//        if(exitCode != 0) 
//            throw new PipelineError("Unable to mount storage using command: $cmd.command:\n\n$output")
//        
//        log.info "Bucket $bucket has been successfully mounted using command: $mountCommand"
//        
//        return "/home/${System.properties['user.name']}/$path"
        
        
          String path = getPath()
        
          String mountCommand = "mkdir -p $path; mountpoint -q main || { gcsfuse --implicit-dirs --stat-cache-ttl 60s --type-cache-ttl 60s $bucket $path ; }"
          return mountCommand
    }
    
    final static String BUCKET_ALREADY_EXISTS_ERROR = 'ServiceException: 409'
    
    /**
     * Create the configured bucket in the specified region, if it does not exist
     */
    @CompileStatic
    void makeBucket() {
        
        init()
        
        assert region != null
        
        // TODO: We should rename what is currently called region to zone to deal with this properly
        String regionWithoutZone = region.replaceAll('-[abc]$','')
        
        List<String> makeBucketCommand = [
            "$sdkHome/bin/gsutil",
            "mb",
            "-c",
            "regional",
             "-l",
             regionWithoutZone,
             "gs://$bucket"
        ]*.toString()
        
        log.info "Creating bucket for work directory using command: " + makeBucketCommand.join(' ')
        ExecutedProcess result = Utils.executeCommand((List<Object>)makeBucketCommand)
        if(result.exitValue != 0) {
            String stdOut = result.out.toString().trim()
            String stdErr = result.err.toString().trim()
            
            println "stdOut: $stdOut\nstdErr: $stdErr\n"
            
            if(stdOut.contains(BUCKET_ALREADY_EXISTS_ERROR) || stdErr.contains(BUCKET_ALREADY_EXISTS_ERROR)) {
                log.info "Bucket already exists: reusing bucket $bucket"
            }
            else {
                throw new Exception("Unable to create bucket $bucket: " + result.out + "\n\n" + result.err)
            }
        }
    }
    
    @CompileStatic
    void init() {
        if(sdkHome == null)
            sdkHome = getSDKHome()
        
        if(region == null)
            this.region = GoogleCloudCommandExecutor.getRegion([:])
        else 
            log.info "Cached region is $region"
                
        if(path == null)
            path = name
    }
    
    @CompileStatic
    String getPath() {
        return path?:name
    }
}
