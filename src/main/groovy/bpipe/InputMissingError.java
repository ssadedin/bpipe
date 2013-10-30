package bpipe;

public class InputMissingError extends PipelineError {
    
    String inputType;
    
    String description;

    public InputMissingError(String inputType, String description) {
        super("Input type " + inputType + " was missing");
        this.inputType = inputType;
        this.description = description;
    }
}
