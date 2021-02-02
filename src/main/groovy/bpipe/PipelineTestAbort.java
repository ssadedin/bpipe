/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bpipe;

import java.util.*;

/**
 * This exception is thrown when the pipeline is aborted because
 * the user ran it in "test"mode.
 */
class PipelineTestAbort extends RuntimeException {
    
    boolean summary = false;
    
    List<String> missingOutputs;
    
    public List<String> getMissingOutputs() {
        return missingOutputs;
    }

    public void setMissingOutputs(List<String> missingOutputs) {
        this.missingOutputs = missingOutputs;
    }

    public boolean isSummary() {
        return summary;
    }

    public void setSummary(boolean summary) {
        this.summary = summary;
    }

    public PipelineTestAbort() {
        super();
    }

    public PipelineTestAbort(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public PipelineTestAbort(String arg0) {
        super(arg0);
    }

    public PipelineTestAbort(Throwable arg0) {
        super(arg0);
    }

    @Override
    public String getMessage() {
        if(this.missingOutputs == null || this.missingOutputs.isEmpty()) {
            return super.getMessage();
        }
        
        StringBuilder b = new StringBuilder(super.getMessage());
        b.append("\n\nto create outputs:\n\n");
        
        HashSet<String> uniqueMissingOutputs = new HashSet<String>(this.missingOutputs);
        
        for(Object o : uniqueMissingOutputs) {
            b.append("    " + String.valueOf(o) + "\n");
        }
        
        return b.toString();
    }
    
    
}
