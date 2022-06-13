package bpipe.storage

import java.nio.file.Files
import java.nio.file.Path

import bpipe.Config
import bpipe.PipelineFile
import bpipe.executor.CommandExecutor
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Log

/**
 * Abstract class that defines an API for Bpipe to interact with 
 * a possibly non-local storage medium.
 * <p>
 * Consider:
 * 
 *  - two Google cloud buckets : bams (input) and results (output)
 *  - the input files come from one bucket, are processed and the actual 
 *    pipeline outputs should apear in results
 *  - both buckets need to get mounted
 *  - then when $input and $output are referenced, they need to point 
 *    to the respective paths where the buckets were mounted
 * 
 * how can this happen?
 * 
 *  - one key problem is that we specify storage *per-command* - so we do
 *    not know where the storage go tmounted until the actual command 
 *    got launched.
 *  - the storage mounting interacts with the executor - eg: google cloud 
 *    executor might mount storage in one location,  local storage might mount it
 *    somewhere else
 *  - so there is a sequence of events like this:
 *    1. the command inputs and outputs are resolved to PipelineFile objects
 *    2. the final config is resolved
 *    3. a command object gets created, including the resolved command (fixing $input and $output 
 *       to specific values)
 *    4. the executor is created, being passed the command object
 *    5. when storage is non-local, it is mounted (potentially)
 *    6. the inputs and outputs are then updated to reflect the actual mounted storage location
 *   
 *  So there is a problem with how the inputs and outputs are resolved, because they are fixed
 *  before the storage is mounted!
 *  
 *  In fact, we resolve them initially to a special format:
 *  
 *     {bpipe:filesystem:path}
 *     
 *  For example
 *  
 *     {bpipe:bams:test.bam}
 *     
 *  In this case there would be a <code>bams</code> entry in the `bpipe.config` filesystems that 
 *  defines the bams storage. 
 *  
 * @author simon.sadedin
 */
@Log
abstract class StorageLayer implements Serializable {
    
    public static final long serialVersionUID = 0L
    
    /**
     * The name of the configuration from which this storage was
     * generated
     */
    String name
    
    public boolean exists(String path) {
        return Files.exists(toPath(path))
    }
    
    abstract Path toPath(String path)
    
    abstract String getMountCommand(CommandExecutor executor)
    
    @Memoized
    static StorageLayer create(String name) {
        
        assert name != 'null'
        
        if(name == null || name == 'local')
            return new LocalFileSystemStorageLayer()
        
        ConfigObject storageConfig = 
            (ConfigObject)bpipe.Config.userConfig['filesystems']
                        .get(name, null)
        
        // We inherit the region value (but not others) from the main config
        if(('region' in bpipe.Config.userConfig) && !storageConfig.containsKey('region')) {
            storageConfig.region = bpipe.Config.userConfig.region
        }
                        
        if(storageConfig == null)
            throw new bpipe.PipelineError(
                "The value ${name} (${name?.class?.name})was supplied as storage, but could not be found in your configuration.\n\n" + 
                "Please add a filesystems entry to your bpipe.config file with an entry for ${name}")
            
        String storageType = storageConfig.getOrDefault('type', name)
           
        storageType = storageType[0].toUpperCase() + storageType[1..-1]
        String className = "bpipe.storage." + storageType + "StorageLayer"
        
        log.info "Configuring storage $name (storage $storageType)"
        StorageLayer result = (StorageLayer)Class.forName(className).newInstance()
        result.name = name 
        
        for(key in ['accessKey','accessSecret','region']) {
            if(result.hasProperty(key) && Config.userConfig.containsKey(key))
                result[key] = Config.userConfig[key]
        }

        for(kvp in storageConfig) {
            if(result.hasProperty(kvp.key))
                result[kvp.key] = storageConfig[kvp.key]
        }
        return result
    }
    
    private static StorageLayer defaultStorage
    
    static StorageLayer getDefaultStorage() {
        if(defaultStorage == null) {
            List<String> storages = Config.listValue('storage')
            log.info "Default storage is ${storages[0]}"
            defaultStorage = create(storages[0])
        }
        return defaultStorage
    }
    
    /**
     * For each raw path, resolve it to the first configured storage where the
     * path exists. If no configured storage has the value, returnes an UnknownStoragePipelineFile
     * instance for that path.
     * <p>
     * <em>Note:</em> files that don't exist are passed back as UnknownStoragePipelineFile instances.
     * This is because they could exist in the context of a command that executes later on,
     * if that command has a different storage configured.
     * 
     * @param rawInputs
     * @return  a list of {@link PipelineFile} objects, one for each raw input
     */
    @CompileStatic
    static List<PipelineFile> resolve(List paths) {
        
        if(paths.every { it instanceof PipelineFile }) 
            return paths
        
        final List storages = storageNames.collect { String storageType -> StorageLayer.create(storageType) }

        return paths.collect { filePathOrString ->
            if(filePathOrString instanceof PipelineFile)
                return (PipelineFile)filePathOrString
                
            String filePath = (String)filePathOrString
                
            StorageLayer layerWithFile = storages.find {  s ->
                try {
                    boolean result =  s.exists(filePath) 
                    log.info "Check $filePath in storage $s: $result"
                    return result
                }
                catch(Exception e) {
                    log.info "Storage system $s returned error resolving $filePath ($e)"
                    return false
                }
            }
            if(layerWithFile) {
                log.info "Input $filePath resolved to storage type $layerWithFile"
                return new PipelineFile(filePath,layerWithFile)
            }
            else
                return new UnknownStoragePipelineFile(filePath)
        }
    }

    @Memoized
    static List<String> getStorageNames() {
        return (['local'] + ((Map<String,Object>)Config.userConfig.getOrDefault('filesystems',[:]))*.key)
    }
}
