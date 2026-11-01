package bpipe.layers

import groovy.transform.CompileStatic

/**
 * Collects the {@link ShellHook}s contributed to a command for each {@link CommandLayer},
 * and renders them around the command text.
 * <p>
 * Each layer that has hooks renders as a subshell:
 * <pre>
 * (
 * __bpipe_job_post() {
 *     __bpipe_rc=$1
 *     set +e
 *     &lt;post hooks, last registered first&gt;
 *     exit $__bpipe_rc
 * }
 * trap '__bpipe_job_post $?' EXIT
 * &lt;pre hooks, in registration order&gt;
 * &lt;inner layer or command&gt;
 * )
 * </pre>
 * The subshell keeps the trap and hook state contained, so an executor that writes the
 * exit status after the command (in the same shell) records it after the post hooks ran.
 * A layer without hooks renders as its inner part unchanged, so commands without hooks are
 * exactly as before.
 */
@CompileStatic
class CommandLayers {

    private final Map<CommandLayer, List<ShellHook>> hooks = new EnumMap<CommandLayer, List<ShellHook>>(CommandLayer)

    void add(CommandLayer layer, ShellHook hook) {
        List<ShellHook> layerHooks = hooks[layer]
        if(layerHooks == null) {
            layerHooks = []
            hooks[layer] = layerHooks
        }
        layerHooks.add(hook)
    }

    List<ShellHook> getHooks(CommandLayer layer) {
        return Collections.unmodifiableList(hooks[layer] ?: (List<ShellHook>)[])
    }

    boolean isEmpty() {
        return hooks.values().every { it.isEmpty() }
    }

    /**
     * @param command   the command text, after all text transformations
     * @return  the text to execute, with the hooks of every layer around the command
     */
    String render(String command) {
        String result = command
        for(CommandLayer layer in CommandLayer.values().reverse()) {
            result = renderLayer(layer, result)
        }
        return result
    }

    private String renderLayer(CommandLayer layer, String inner) {

        List<ShellHook> layerHooks = hooks[layer]
        if(!layerHooks)
            return inner

        String postFunction = "__bpipe_${layer.name().toLowerCase()}_post"

        StringBuilder b = new StringBuilder()
        b.append('(\n')
        b.append("${postFunction}() {\n")
        b.append('    __bpipe_rc=$1\n')
        b.append('    set +e\n')
        for(ShellHook hook in layerHooks.reverse()) {
            if(hook.post)
                appendBlock(b, hook.post)
        }
        b.append('    exit $__bpipe_rc\n')
        b.append('}\n')
        b.append("trap '${postFunction} \$?' EXIT\n")
        for(ShellHook hook in layerHooks) {
            if(hook.pre)
                appendBlock(b, hook.pre)
        }
        appendBlock(b, inner)
        b.append(')\n')
        return b.toString()
    }

    /**
     * Append text verbatim (never re-indented, so heredocs inside hooks stay valid)
     */
    private static void appendBlock(StringBuilder b, String text) {
        b.append(text)
        if(!text.endsWith('\n'))
            b.append('\n')
    }
}
