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
package bpipe.executor;

import java.util.List;
import java.util.Map;

import bpipe.Command;

/**
 * In certain situations, Bpipe needs to "probe" a command to see what would be the actual
 * final outputs and inputs that are referenced by the command. In these situations,
 * it sends it through to execution, but using this dummy executor that does not really execute
 * the command.
 * 
 * @author Simon
 */
public class ProbeCommandExecutor implements CommandExecutor {

    private static final long serialVersionUID = 1L;

    @Override
    public void start(Map cfg, Command command, Appendable outputLog, Appendable errorLog) {
    }

    @Override
    public String status() {
        return null;
    }

    @Override
    public int waitFor() {
        return 0;
    }

    @Override
    public void stop() {
    }
    
    public void cleanup() {
    }

    @Override
    public List<String> getIgnorableOutputs() {
        return null;
    }

    @Override
    public String statusMessage() {
        return "Probe command executor";
    }

}
