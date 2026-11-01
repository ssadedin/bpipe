package bpipe.staging

import java.nio.file.Files
import java.nio.file.Path

import bpipe.PipelineFile
import bpipe.storage.LocalFileSystemStorageLayer
import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * Works out which of a command's files to stage and rewrites the command to use the
 * staged paths.
 * <p>
 * Only local files are candidates. Inputs are staged if their path appears as a whole
 * token in the command (see {@link StagingPathRewriter}) and they are regular files;
 * their companion files ({@link Companions}) are staged with them. All declared outputs
 * are planned for return, so outputs written as a side effect next to a staged output
 * are returned too. Anything not rewritten keeps referring to its canonical location.
 */
@Log
@CompileStatic
class StagingPlanner {

    static class StagedCommand {
        
        StagingPlan plan
        
        /**
         * The command text with staged paths substituted (hooks are added separately,
         * see {@link StagingArea#addHooks})
         */
        String command
    }

    static StagedCommand stage(StagingArea area, String stageDir, String commandText, List<PipelineFile> inputs, List<PipelineFile> outputs) {

        StagingPlan plan = new StagingPlan(stageDir: stageDir)

        // Path as it may appear in the command => canonical path
        Map<String,Path> inputTokens = tokensFor(inputs.findAll { PipelineFile f -> Files.isRegularFile(canonical(f)) })
        Map<String,Path> outputTokens = tokensFor(outputs)

        // An output that is also an input is treated as an input
        outputTokens.keySet().removeAll(inputTokens.keySet())

        Map<String,String> replacements = [:]
        inputTokens.each { String token, Path path -> replacements[token] = area.stagedInputPath(stageDir, path) }
        outputTokens.each { String token, Path path -> replacements[token] = area.stagedOutputPath(stageDir, path) }

        StagingPathRewriter.Result rewritten = StagingPathRewriter.rewrite(commandText, replacements)

        for(String token in rewritten.replaced) {
            Path input = inputTokens[token]
            if(input == null)
                continue
            addInput(plan, area, input)
            for(Path companion in Companions.find(input, area.companions)) {
                addInput(plan, area, companion)
            }
        }

        for(Path output in new LinkedHashSet<Path>(outputTokens.values())) {
            plan.outputs[output.toString()] = area.stagedOutputPath(stageDir, output)
        }

        log.info "Staging plan for area $area.name: $plan"

        return new StagedCommand(plan: plan, command: rewritten.text)
    }

    private static void addInput(StagingPlan plan, StagingArea area, Path input) {
        plan.inputs[input.toString()] = area.stagedInputPath(plan.stageDir, input)
    }

    /**
     * @return the forms in which each local file may appear in a command (as rendered,
     *         and as an absolute path) mapped to its canonical absolute path
     */
    private static Map<String,Path> tokensFor(List<PipelineFile> files) {
        Map<String,Path> result = new LinkedHashMap<String,Path>()
        for(PipelineFile f in files) {
            if(!(f.storage instanceof LocalFileSystemStorageLayer))
                continue
            Path path = canonical(f)
            result[f.toString()] = path
            result[path.toString()] = path
        }
        return result
    }

    private static Path canonical(PipelineFile f) {
        return f.toPath().toAbsolutePath().normalize()
    }
}
