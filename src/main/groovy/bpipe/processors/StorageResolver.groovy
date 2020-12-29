package bpipe.processors

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.*

import bpipe.*
import bpipe.executor.CommandExecutor
import bpipe.storage.StorageLayer
import groovy.transform.CompileStatic
import groovy.util.logging.Log

@Log
@CompileStatic
class StorageResolver implements CommandProcessor {
    
    final static Pattern MOUNT_POINT_PATTERN = ~/\{bpipe:([a-zA-Z0-9]{1,}):(.*?)\}/
    
    final static Pattern REGION_PATTERN = ~/\{region:(.*?)\}/

    final CommandExecutor commandExecutor
    
    public StorageResolver(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
        command.command = replaceStorageMountPoints(command)
    }
    
    protected String replaceStorageMountPoints(Command command) {
        
        StringBuffer regionCommand = 
            transferBEDsAndReplaceRegionReferences(command.processedConfig, command.command)
        
        log.info "After replacing regions, command is: $regionCommand"
        
        Matcher matches = MOUNT_POINT_PATTERN.matcher(regionCommand)
        StringBuffer newCommand = new StringBuffer(command.command.size())
        log.info "Checking for mount paths in $command.command"
        while(matches.find()) {
            String path = matches.group(2)
            String storageName = matches.group(1)
            
            log.info "Replacement path is $path via storage $storageName"
            
            StorageLayer storage = StorageLayer.create(storageName)
//            String mountedPath = commandExecutor.localPath(storageName)
            commandExecutor.mountStorage(storage)
            String mountedPath = commandExecutor.localPath(storageName) // assumption that storage mounts under its own name, not really true
            log.info "Storage $storageName mounted to path $mountedPath in executor $commandExecutor"
            
            // Strip leading slashes as we need to reference this relative to the local path of the mount returned by the executor
            path = path.replaceAll('^/*','')
            
            String newPath = mountedPath ? "$mountedPath/$path" : path
            matches.appendReplacement(newCommand, newPath)
        }
        matches.appendTail(newCommand)
        
        
        log.info "Replacing storage mount points in $command.command => $newCommand"
        return newCommand
    }

    /**
     *  Searches for unresolved region file references in the command and replaces
     *  them with appropriate paths for the storage system the command is using.
     *  
     *  If the storage system is non-local, transfers the BED file so that the 
     *  remote system will have access to it, and transforms the path to the
     *  correct path on the other system.
     * 
     * @param command
     * @return  command with region references removed / replaced
     */
    @CompileStatic
    private StringBuffer transferBEDsAndReplaceRegionReferences(final Map cfg, final String command) {

        String defaultStorageName = Config.listValue(cfg, 'storage')[0]
        StorageLayer defaultStorage = StorageLayer.create(defaultStorageName)

        // First replace the regions, as these will turn into storage mounts?
        StringBuffer regionCommand = new StringBuffer()
        Matcher regionMatches = REGION_PATTERN.matcher(command)
        while(regionMatches.find()) {
            String localPath = regionMatches.group(1)
            String mountPoint = commandExecutor.localPath(defaultStorageName)
            String remotePath = mountPoint ? (mountPoint + '/' + localPath) :  localPath
            regionMatches.appendReplacement(regionCommand, remotePath)
            if(defaultStorageName && defaultStorageName != 'local') {
                Path toPath = defaultStorage.toPath(localPath)
                log.info "Copying region from $localPath -> $toPath"
                if(!Files.exists(toPath))
                    Files.copy(new File(localPath).toPath(), toPath)
            }
        }
        regionMatches.appendTail(regionCommand)
        return regionCommand
    }
}

