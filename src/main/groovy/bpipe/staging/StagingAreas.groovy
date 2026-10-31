package bpipe.staging

import bpipe.Config
import bpipe.PipelineError
import groovy.transform.CompileStatic

/**
 * Creates {@link StagingArea} instances from the <code>stagingAreas</code> configuration block:
 * <pre>
 * stagingAreas {
 *     scratch {
 *         type = 'directory'
 *         path = '/scratch'
 *     }
 * }
 * </pre>
 */
@CompileStatic
class StagingAreas {

    static final Map<String,Class<? extends StagingArea>> TYPES = [
        directory: DirectoryStagingArea
    ]

    static StagingArea create(String name) {
        return create(name, (Map)Config.userConfig.getOrDefault('stagingAreas', null))
    }

    static StagingArea create(String name, Map areasConfig) {

        if(areasConfig == null)
            throw new PipelineError(
                "The staging area '$name' was configured, but no stagingAreas block was found in your configuration.\n\n" +
                "Please add a stagingAreas entry to your bpipe.config file with an entry for '$name'.")

        Map areaConfig = (Map)areasConfig.getOrDefault(name, null)
        if(areaConfig == null)
            throw new PipelineError(
                "The staging area '$name' was configured, but no entry for it was found in the stagingAreas block of your configuration.")

        String type = String.valueOf(areaConfig.getOrDefault('type', 'directory'))
        Class<? extends StagingArea> areaClass = TYPES[type]
        if(areaClass == null)
            throw new PipelineError(
                "The staging area '$name' has type '$type', which is not supported. Supported types are: ${TYPES.keySet().join(', ')}")

        StagingArea area = areaClass.newInstance()
        area.name = name

        if(areaConfig.containsKey('companions'))
            area.companions = Config.listValue(areaConfig, 'companions')

        if(area instanceof DirectoryStagingArea) {
            if(!areaConfig.getOrDefault('path', null))
                throw new PipelineError("The staging area '$name' must have a path configured")
            ((DirectoryStagingArea)area).setPath(String.valueOf(areaConfig.path))
        }

        return area
    }
}
