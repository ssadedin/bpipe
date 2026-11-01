package bpipe.layers

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Shell to run before and / or after the inner part of a {@link CommandLayer}.
 * <p>
 * Contract for hook authors:
 * <ul>
 *   <li><b>pre</b> runs before the inner part of the layer, in registration order. It must
 *       <code>exit</code> with a non-zero status if it fails (do not rely on errexit being set).
 *       Exiting still runs the post hooks of the layer.
 *   <li><b>post</b> runs when the layer exits, for whatever reason (normal completion, an
 *       explicit <code>exit</code>, errexit, a failed pre hook), in reverse registration order.
 *       The exit status of the layer is in <code>$__bpipe_rc</code>: a post hook may set it to
 *       fail the command, and should leave it unchanged otherwise. Post hooks run with errexit
 *       disabled, so a failing statement does not prevent later post hooks from running.
 *   <li>Both must be POSIX shell, as they may run under <code>sh</code> as well as <code>bash</code>.
 * </ul>
 */
@CompileStatic
@ToString(includeNames=true)
class ShellHook {

    /**
     * Identifies the contributor of the hook in logs
     */
    String name

    String pre

    String post
}
