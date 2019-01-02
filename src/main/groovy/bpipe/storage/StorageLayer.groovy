package bpipe.storage

import java.nio.file.Path

import bpipe.Config
import bpipe.executor.CommandExecutor
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Log

/**
 * Abstract class that defines an API for Bpipe to interact with 
 * a possibly non-local storage medium.
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
    
    abstract boolean exists(String path)
    
    abstract Path toPath(String path)
    
    abstract String mount(CommandExecutor executor)
    
    @Memoized
    static StorageLayer create(String name) {
        
        assert name != 'null'
        
        if(name == null || name == 'local')
            return new LocalFileSystemStorageLayer()
        
        ConfigObject storageConfig = 
            (ConfigObject)bpipe.Config.userConfig['filesystems']
                        .get(name, null)
        
        if(storageConfig == null)
            throw new bpipe.PipelineError(
                "The value ${name} (${name?.class?.name})was supplied as storage, but could not be found in your configuration.\n\n" + 
                "Please add a filesystems entry to your bpipe.config file with an entry for ${name}")
            
        String storageType = storageConfig.getOrDefault('type', null)
        if(!storageType)
            throw new bpipe.PipelineError("The filesystem configuration for $name does not specify the type of storage. Please specify a type configuration element for this filesystem")
            
        storageType = storageType[0].toUpperCase() + storageType[1..-1]
        String className = "bpipe.storage." + storageType + "StorageLayer"
        
        StorageLayer result = (StorageLayer)Class.forName(className).newInstance()
        result.name = name 
        
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
}
