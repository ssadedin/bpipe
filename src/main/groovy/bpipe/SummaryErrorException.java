/*
* Copyright (c) 2014 MCRI, authors
*
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package bpipe;

import java.util.HashSet;
import java.util.List;

/**
 * A user level error that occurred during pipeline execution.
 * Such errors are shown to the user without accompanying stack
 * traces.  Descriptions in the error messages should be 
 * friendly and understandable and not refer to code 
 * artefacts.
 */
class SummaryErrorException extends PipelineError {
    
    private static final long serialVersionUID = 1L;

    private HashSet<PipelineError> summarisedErrors = new HashSet<PipelineError>();
    
    public SummaryErrorException(List<PipelineError> children) throws Exception {
        super();
        for(Exception e : children)  {
            if(e instanceof SummaryErrorException) {
                this.summarisedErrors.addAll(((SummaryErrorException)e).summarisedErrors);
            }
            else
            if(e instanceof PipelineError) {
                this.summarisedErrors.add((PipelineError)e);
            }
            else
                throw e;
        }
        
    }
    
    public String getMessage() {
        
        return "One or more parallel stages aborted. The following messages were reported:\n\n" + Utils.formatErrors(summarisedErrors);
    }

    public SummaryErrorException(Throwable arg0) {
        super(arg0);
    }
    
}
