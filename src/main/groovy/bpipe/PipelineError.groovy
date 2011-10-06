package bpipe

/**
 * All errors thrown during pipeline processing are wrapped
 * in PipelineError
 */
class PipelineError extends RuntimeException {

    public PipelineError() {
        super();
    }

    public PipelineError(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public PipelineError(String arg0) {
        super(arg0);
    }

    public PipelineError(Throwable arg0) {
        super(arg0);
    }
    
}
