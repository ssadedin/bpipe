package bpipe.storage

import bpipe.PipelineFile
import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * A stand-in for an input that could not be resolved to a real file.
 * <p>
 * These are only ever used when running in dev mode, where the point is to keep
 * evaluating the stage just far enough to be able to show the user the command
 * that would have been executed, together with the inputs that were missing.
 * Since the command is never actually launched in this state, it is enough for
 * the placeholder to render as the specification the user asked for.
 * <p>
 * Note that this is deliberately <em>not</em> an {@link UnknownStoragePipelineFile},
 * as the latter throws when rendered to a command.
 */
@Log
@CompileStatic
class UnresolvedInputPipelineFile extends PipelineFile {

    /**
     * The specification the user asked for, as written in the pipeline.
     */
    String spec

    UnresolvedInputPipelineFile(String spec) {
        super(spec, new LocalFileSystemStorageLayer())
        this.spec = spec

        log.info "Created unresolved input placeholder: $spec"
    }

    /**
     * Renders as the literal spec requested by the user, so that the attempted
     * command can be displayed in dev mode.
     */
    @Override
    String renderToCommand() {
        path
    }
}
