package bpipe.processors

import bpipe.Command
import bpipe.CommandProcessor
import bpipe.Config
import bpipe.PipelineError
import bpipe.PipelineFile
import bpipe.ResourceUnit
import bpipe.staging.StagingArea
import bpipe.staging.StagingAreas
import bpipe.staging.StagingPlan
import bpipe.staging.StagingPlanner
import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * Applies the staging area configured for a command (the <code>staging</code> attribute):
 * rewrites paths of staged files and wraps the command with the shell that moves them
 * into and out of the area.
 * <p>
 * After {@link #transform}, {@link #area} and {@link #plan} are set if the command is staged,
 * for use by host-side staging hooks.
 */
@Log
@CompileStatic
class StagingProcessor implements CommandProcessor {

    StagingArea area

    StagingPlan plan

    @Override
    public void transform(Command command, List<ResourceUnit> resources) {

        Map cfg = command.processedConfig

        Object stagingName = cfg.getOrDefault('staging', null)
        if(!stagingName)
            return

        if(((Map)cfg.getOrDefault('container', null))?.getOrDefault('type', null))
            throw new PipelineError(
                "Command $command.name is configured to use both staging area '$stagingName' and a container, which is not yet supported")

        this.area = StagingAreas.create(String.valueOf(stagingName))

        String stageDir = area.stageDir(String.valueOf(Config.config.pid), command.id)

        List<PipelineFile> inputs = command.inputs ?: (List<PipelineFile>)[]
        List<PipelineFile> outputs = command.outputs ?: (List<PipelineFile>)[]

        StagingPlanner.StagedCommand staged = StagingPlanner.stage(area, stageDir, command.command, inputs, outputs)

        this.plan = staged.plan

        log.info "Command $command.id staged in area $area.name with ${plan.inputs.size()} inputs and ${plan.outputs.size()} outputs"

        command.command = staged.command
    }
}
