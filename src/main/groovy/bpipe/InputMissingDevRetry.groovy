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

/**
 * Thrown in place of an {@link InputMissingError} when the stage that could not
 * resolve its inputs is being run interactively in dev mode.
 * <p>
 * Being a {@link PipelineDevRetry} it is picked up by the normal dev interaction
 * loop, so the user gets a chance to fix the pipeline (which is reloaded and the
 * stage retried) rather than having the whole run abort.
 */
class InputMissingDevRetry extends PipelineDevRetry {

    InputMissingDevRetry(String message) {
        super(message)
    }

    InputMissingDevRetry(String message, Throwable cause) {
        super(message, cause)
    }
}
