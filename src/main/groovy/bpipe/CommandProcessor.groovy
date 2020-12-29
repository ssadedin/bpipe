package bpipe

/**
 * Command processors transform a command as given by the user
 * in preparation for execution in the target environment
 * 
 * @author Simon Sadedin
 */
interface CommandProcessor {
    void transform(Command command, List<ResourceUnit> resources) 
}
