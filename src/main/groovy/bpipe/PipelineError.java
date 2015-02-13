/*
* Copyright (c) 2011 MCRI, authors
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

/**
 * A user level error that occurred during pipeline execution.
 * Such errors are shown to the user without accompanying stack
 * traces.  Descriptions in the error messages should be 
 * friendly and understandable and not refer to code 
 * artefacts.
 */
class PipelineError extends RuntimeException {

    PipelineContext ctx;
    
    boolean summary = false;
    
    public boolean isSummary() {
        return summary;
    }

    public void setSummary(boolean summary) {
        this.summary = summary;
    }

    public void setCtx(PipelineContext ctx) {
        this.ctx = ctx;
    }

    public PipelineContext getCtx() {
        return ctx;
    }

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

    public PipelineError(String description, PipelineContext ctx) {
        this(description);
        this.ctx = ctx;
    }
    
}
