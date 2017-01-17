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
 * Thrown if a pipeline is paused to terminate branches that
 * would otherwise execute.
 */
class PipelinePausedException extends RuntimeException {

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

    public PipelinePausedException() {
        super();
    }

    public PipelinePausedException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public PipelinePausedException(String arg0) {
        super(arg0);
    }

    public PipelinePausedException(Throwable arg0) {
        super(arg0);
    }

    public PipelinePausedException(String description, PipelineContext ctx) {
        this(description);
        this.ctx = ctx;
    }
    
}
