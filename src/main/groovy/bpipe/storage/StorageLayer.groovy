package bpipe.storage

import java.nio.file.Path

abstract class StorageLayer implements Serializable {
    
    public static final long serialVersionUID = 0L

    abstract boolean exists(String path)
    
    abstract Path toPath(String path)
    
    static StorageLayer create(String name) {
        
        if(name == null || name == 'local')
            return new LocalFileSystemStorageLayer()
        
        ConfigObject storageConfig = 
            bpipe.Config.userConfig['filesystems']
                        .get(name, null)
        
        if(storageConfig == null)
            throw new bpipe.PipelineError(
                "The value ${name} was supplied as storage, but could not be found in your configuration.\n\n" + 
                "Please add a filesystems entry to your bpipe.config file with an entry for ${name}")
            
        String className = "bpipe.storage." + storageConfig.type + "StorageLayer"
        
        StorageLayer result = Class.forName(className).newInstance()
        
        for(kvp in storageConfig) {
            if(result.hasProperty(kvp.key))
                result[kvp.key] = storageConfig[kvp.key]
        }
        return result
    }
}
