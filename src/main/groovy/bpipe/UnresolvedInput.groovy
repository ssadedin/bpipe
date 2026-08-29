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
package bpipe

import groovy.transform.CompileStatic

/**
 * Records an input that a pipeline stage asked for, but which could not be
 * resolved to an actual file.
 * <p>
 * When running in dev mode, unresolved inputs are collected on the
 * {@link PipelineContext} and substituted with {@link bpipe.storage.UnresolvedInputPipelineFile}
 * placeholders. This lets the stage run as far as the command it would have
 * executed, so the user can see the attempted command along with an explanation
 * of which inputs were missing, and fix the pipeline without the run aborting.
 */
@CompileStatic
class UnresolvedInput {

    /**
     * The specification that could not be resolved, as the user wrote it.
     * Either a 'from' pattern, or a file extension in the case of an
     * <code>$input.&lt;ext&gt;</code> reference.
     */
    String spec

    /**
     * Where the request for this input came from, used to word the error
     * shown to the user (eg. <code>from('a.txt')</code> or <code>input.a</code>)
     */
    String source

    UnresolvedInput(String spec, String source) {
        this.spec = spec
        this.source = source
    }

    String toString() {
        "$spec ($source)"
    }
}
