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
package bpipe.executor;

/**
 * Marker interface for executors that can capture post-completion resource
 * utilisation (CPU time, peak memory, etc.).
 * <p>
 * Implementations should:
 * <ul>
 *   <li>Return {@code null} if capture is unavailable or fails irrecoverably.</li>
 *   <li>Not throw exceptions — swallow internal errors and return {@code null}.</li>
 *   <li>Respect the configured {@code utilisation.maxWaitSeconds} bound.</li>
 *   <li>Return as soon as "good enough" data is available (at least one of
 *       CPU time or peak RSS is non-null and non-zero).</li>
 * </ul>
 * <p>
 * Called from {@link bpipe.CommandManager#cleanup(bpipe.Command)} after the
 * command finishes but before {@link CommandExecutor#cleanup()}.
 */
public interface UtilisationCapturingExecutor {

    /**
     * Capture utilisation data for the most recently completed command.
     *
     * @return captured utilisation summary, or {@code null} if capture
     *         failed or is not available for this executor instance.
     */
    CommandUtilisation captureUtilisation();
}
