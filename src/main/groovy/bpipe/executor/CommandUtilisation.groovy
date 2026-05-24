/*
 * Copyright (c) MCRI, authors
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
package bpipe.executor

/**
 * Executor-agnostic summary of actual resource utilisation for a completed command.
 * <p>
 * Populated by executors that implement {@link UtilisationCapturingExecutor} and
 * stored on {@link bpipe.Command#utilisation}. Written to the result XML so that
 * {@link bpipe.cmd.StatsCommand} can display "used" vs "requested" resources
 * without knowing which executor or accounting tool produced the data.
 */
class CommandUtilisation implements Serializable {

    static final long serialVersionUID = 1L

    /** Wall-clock seconds as reported by the executor's accounting system */
    Long elapsedSeconds

    /** Total CPU seconds consumed across all cores */
    Long cpuSecondsTotal

    /** Effective cores used: cpuSecondsTotal / elapsedSeconds, precomputed */
    Double coresUsed

    /** Peak resident set size in bytes */
    Long maxRssBytes

    /** Peak virtual memory in bytes (optional, may be null) */
    Long maxVmemBytes

    /** Executor-reported terminal state (e.g. "COMPLETED", "OUT_OF_MEMORY") */
    String state

    /** Which mechanism captured this data (e.g. "sacct", "qstat", "cgroups") */
    String source

    /** Epoch millis when the capture finished, for staleness debugging */
    Long capturedAtMs

    /** Arbitrary executor-specific key/value pairs (NodeList, Partition, etc.) */
    Map<String,String> extras = [:]
}
