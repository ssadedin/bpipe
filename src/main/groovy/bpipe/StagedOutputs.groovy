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
package bpipe

import java.nio.file.Path
import java.nio.file.Paths

import bpipe.storage.LocalFileSystemStorageLayer
import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * Describes how the outputs of a single command will be satisfied from staged
 * files instead of by running the command.
 * <p>
 * A plan is created by {@link StagedOutputs#plan} at the time the command is
 * created (in {@link PipelineContext#async}), but is only acted on at the very
 * last moment, in {@link bpipe.executor.ThrottledDelegatingCommandExecutor#doStart},
 * after all the late-bound processing of the command (resource allocation,
 * memory / thread substitution, container and environment wrapping) has
 * happened. That way staging exercises as much of the pipeline as possible: the
 * only thing skipped is the actual execution of the command.
 *
 * @author ssadedin
 */
@CompileStatic
class StagingPlan implements Serializable {

    public static final long serialVersionUID = 0L

    /**
     * Map of canonical output path => staged file that will be copied to create it.
     * Ordering follows the command's outputs so that the generated script is stable.
     */
    LinkedHashMap<String,String> files = new LinkedHashMap()

    /**
     * Map of canonical output path => the staging directory the file was found in.
     * This is recorded in the output meta data so that a later run can tell whether
     * an output came from staging (and from which staging directory).
     */
    Map<String,String> sources = [:]

    /**
     * Index files to copy along with each output: canonical output path => (canonical
     * index path => staged index file). These are not outputs of the command, so they
     * are not tracked in output meta data or dependencies: they are simply copied if
     * present, immediately after the file they index (see {@link StagedOutputs#findStagedIndexes}).
     */
    Map<String,LinkedHashMap<String,String>> indexes = [:]

    /**
     * @return  the paths (relative to the run directory where possible) of the index
     *          files that will be copied, for display
     */
    List<String> getIndexFiles() {
        List<String> result = []
        if(indexes == null) // plan serialized by a version without index support
            return result
        for(Map<String,String> outputIndexes in indexes.values()) {
            for(String path in outputIndexes.keySet())
                result << (StagedOutputs.relativePath(path) ?: path)
        }
        return result
    }

    /**
     * Distinct staging directories that contributed to this plan, for display
     */
    List<String> getStagedDirs() {
        new ArrayList<String>(sources.values()).unique(false)
    }

    /**
     * @return the staging directory that the given output will be copied from,
     *         or null if the output is not part of this plan
     */
    String sourceFor(PipelineFile output) {
        sources[StagedOutputs.canonicalPath(output)]
    }

    /**
     * Create the shell script that copies the staged files into place.
     * <p>
     * The original command is embedded as a comment so that anything that records
     * the command text (the command log, output meta data, the report) still shows
     * what would have run. Note that timestamps are deliberately NOT preserved
     * (no <code>cp -p</code>): the copies must be newer than the stage's inputs
     * or they will be treated as out of date.
     */
    String toScript(String originalCommand) {
        List<String> lines = []
        lines << "# Bpipe: outputs copied from staged directory ${stagedDirs.join(', ')}".toString()
        lines << "# The following command was NOT executed:"
        originalCommand.readLines().each { String line -> lines << "#     " + line }
        lines << "set -e"
        files.each { String output, String staged ->
            if(new File(staged).isDirectory()) {
                // Copy the contents rather than the directory itself so that an existing
                // output directory does not end up with the staged directory nested inside it
                lines << "mkdir -p ${quote(output)}".toString()
                lines << "cp -R ${quote(staged + '/.')} ${quote(output + '/')}".toString()
            }
            else {
                lines << "mkdir -p ${quote(new File(output).parent)}".toString()
                lines << "cp ${quote(staged)} ${quote(output)}".toString()

                // Indexes are copied after the file they index, so that they end up newer
                // than it (tools such as samtools warn about an index older than its file)
                indexes?.get(output)?.each { String index, String stagedIndex ->
                    lines << "cp ${quote(stagedIndex)} ${quote(index)}".toString()
                }
            }
        }
        return lines.join('\n') + '\n'
    }

    @CompileStatic
    static String quote(String value) {
        "'" + value.replace("'", "'\\''") + "'"
    }
}

/**
 * Support for running a pipeline with "staged" outputs (<code>--staged &lt;dir&gt;</code>).
 * <p>
 * A staging directory mirrors the directory hierarchy that the pipeline sees from
 * its run directory. Before a command is executed, each of its outputs is looked up
 * by its path relative to the run directory in each staging directory (first match wins).
 * If <i>all</i> of the command's outputs are found, the command is not executed: the
 * staged files are copied into place instead, and the command that would have run is
 * printed. If only some are found, the command runs normally and a warning is printed,
 * since a mixture of staged and real outputs from one command is unlikely to be coherent.
 * <p>
 * This lets a pipeline under development skip expensive steps, and lets tests present
 * specific data features (e.g. QC failures) to downstream stages without needing full
 * end-to-end data that produces them.
 * <p>
 * When an output is copied, known index files for it (eg: <code>.bai</code> for a
 * <code>.bam</code>) that are present alongside it in the same staging directory are copied
 * too, even though they are not declared outputs (see {@link #findStagedIndexes}).
 * <p>
 * Outputs created from staged files are marked in their output meta data
 * ({@link OutputMetaData#stagedFrom}), so that a subsequent run that does not use the
 * same staging directory treats them as out of date rather than silently reusing
 * dummy data (see {@link #isOutOfDate}).
 *
 * @author ssadedin
 */
@Log
class StagedOutputs {

    /**
     * Default index file extensions, by the extension of the file they index. User
     * configuration (<code>staging { indexes = [...] }</code> in bpipe.config) is merged
     * over these by extension, so that adding one type does not lose the others, and an
     * empty list disables one.
     * <p>
     * Each index extension is tried in both common naming conventions: appended to the full
     * file name (<code>x.bam.bai</code>) and replacing the matched extension
     * (<code>x.bai</code>). Since an index is only copied if it exists, trying a convention
     * that is not used for a type is harmless.
     */
    final static Map<String,List<String>> DEFAULT_INDEXES = [
        'bam'      : ['bai', 'csi'],
        'cram'     : ['crai'],
        'vcf'      : ['idx'],
        'vcf.gz'   : ['tbi', 'csi'],
        'bcf'      : ['csi'],
        'bed.gz'   : ['tbi'],
        'gff.gz'   : ['tbi'],
        'gff3.gz'  : ['tbi'],
        'gtf.gz'   : ['tbi'],
        'fa'       : ['fai'],
        'fasta'    : ['fai'],
        'fa.gz'    : ['fai', 'gzi'],
        'fasta.gz' : ['fai', 'gzi'],
    ].asImmutable()

    /**
     * @return  the index extensions to look for, keyed by the extension of the
     *          file they index: the defaults merged with any user configuration
     */
    static Map<String,List<String>> getIndexExtensions() {

        Map<String,List<String>> result = new LinkedHashMap<String,List<String>>(DEFAULT_INDEXES)

        // Note: userConfig is null if no configuration has been loaded (eg: in unit tests)
        Map userConfig = Config.userConfig
        Object staging = userConfig?.containsKey('staging') ? userConfig.get('staging') : null
        if(!(staging instanceof Map) || !((Map)staging).containsKey('indexes'))
            return result

        Object configured = ((Map)staging).get('indexes')
        if(!(configured instanceof Map))
            throw new PipelineError("The staging.indexes setting in bpipe.config should be a map of file extension => list of index extensions, eg: [bam: ['bai']], but was: $configured")

        ((Map)configured).each { ext, indexExts ->
            List<String> values = (indexExts instanceof Collection) ? ((Collection)indexExts)*.toString() : (indexExts == null ? [] : [indexExts.toString()])
            result[stripDot(ext.toString())] = values.collect { stripDot(it) }.grep { it }
        }

        return result
    }

    @CompileStatic
    private static String stripDot(String ext) {
        ext.trim().replaceAll('^\\.', '')
    }

    /**
     * Find index files for a staged output that are present in the same staging directory as it.
     *
     * @param output        the output being staged
     * @param stagedFile    the staged file that the output will be copied from
     * @param indexExts     index extensions, as returned by {@link #getIndexExtensions}
     *
     * @return  map of canonical index path => staged index file, in the order they were looked up
     */
    @CompileStatic
    static LinkedHashMap<String,String> findStagedIndexes(PipelineFile output, File stagedFile, Map<String,List<String>> indexExts) {

        LinkedHashMap<String,String> result = new LinkedHashMap()
        if(stagedFile.isDirectory())
            return result

        File outputFile = new File(canonicalPath(output))
        String name = outputFile.name

        // Most specific (longest) matching extension wins, eg: vcf.gz rather than gz
        String ext = indexExts.keySet()
            .findAll { String e -> name.endsWith('.' + e) && name.length() > e.length() + 1 }
            .max { String e -> e.length() }

        if(ext == null || !indexExts[ext])
            return result

        String stem = name.substring(0, name.length() - ext.length() - 1)
        for(String indexExt in indexExts[ext]) {
            for(String indexName in [name + '.' + indexExt, stem + '.' + indexExt]) {
                File stagedIndex = new File(stagedFile.parentFile, indexName)
                if(stagedIndex.isFile()) {
                    result[new File(outputFile.parentFile, indexName).path] = stagedIndex.path
                }
            }
        }
        return result
    }

    /**
     * Configure the staging directories from a comma separated list (the <code>--staged</code>
     * option) or a list (the <code>staged</code> entry in bpipe.config).
     */
    static void configure(Object spec) {
        List<String> dirs = (spec instanceof Collection) ? ((Collection)spec)*.toString() : String.valueOf(spec).tokenize(',')
        Config.config.stagedDirs = dirs*.trim().grep { it }.collect { String dir ->
            File f = new File(dir)
            if(!f.isDirectory())
                throw new PipelineError("The staged outputs directory $dir does not exist or is not a directory")
            f.canonicalPath
        }
        log.info "Configured staged output directories: ${Config.config.stagedDirs}"
    }

    @CompileStatic
    static List<String> getStagedDirs() {
        (List<String>)Config.config.getOrDefault('stagedDirs', [])
    }

    @CompileStatic
    static boolean isEnabled() {
        !getStagedDirs().isEmpty()
    }

    /**
     * Determine whether the given outputs can be satisfied from staged files.
     *
     * @return  a plan if every output was found in a staging directory, otherwise null
     */
    @CompileStatic
    static StagingPlan plan(String stageName, List<PipelineFile> outputs) {

        if(!isEnabled() || !outputs)
            return null

        StagingPlan plan = new StagingPlan()
        List<PipelineFile> missing = []
        for(PipelineFile output in outputs) {
            Map.Entry<String,File> staged = findStaged(output)
            if(staged == null) {
                missing << output
                continue
            }
            String canonical = canonicalPath(output)
            plan.files[canonical] = staged.value.path
            plan.sources[canonical] = staged.key
        }

        if(!missing) {
            log.info "All outputs ${outputs} of command in stage $stageName are staged: will copy from staging directories"
            addIndexes(plan, outputs)
            return plan
        }

        if(plan.files) {
            String msg = "WARNING: stage $stageName has staged files for only some of its outputs " +
                         "(staged: ${plan.files.keySet().collect { relativePath(it) }.join(', ')}; " +
                         "not staged: ${missing.collect { it.path }.join(', ')}). The command will be executed normally."
            log.warning msg
            println msg
        }
        else {
            log.info "No staged files for outputs $outputs of stage $stageName"
        }
        return null
    }

    /**
     * Add index files for each of the plan's outputs. An index that is itself a declared
     * output of the command is already part of the plan, and is not added again.
     */
    @CompileStatic
    private static void addIndexes(StagingPlan plan, List<PipelineFile> outputs) {
        Map<String,List<String>> indexExts = getIndexExtensions()
        for(PipelineFile output in outputs) {
            String canonical = canonicalPath(output)
            LinkedHashMap<String,String> indexes = findStagedIndexes(output, new File(plan.files[canonical]), indexExts)
            indexes.keySet().removeAll(plan.files.keySet())
            if(indexes) {
                log.info "Staged index files for $output: ${indexes.values()}"
                plan.indexes[canonical] = indexes
            }
        }
    }

    /**
     * Find the staged counterpart of the given output.
     *
     * @return  an entry of staging directory => staged file, or null if the output
     *          is not staged, or cannot be staged (non-local storage, globs, or
     *          paths outside the run directory)
     */
    @CompileStatic
    static Map.Entry<String,File> findStaged(PipelineFile output) {

        if(output.storage != null && !(output.storage instanceof LocalFileSystemStorageLayer)) {
            log.info "Output $output is not on local storage: staging not supported"
            return null
        }

        if(output.path.contains('*'))
            return null

        String relative = relativePath(canonicalPath(output))
        if(relative == null) {
            log.info "Output $output is outside the run directory: staging not supported"
            return null
        }

        for(String dir in getStagedDirs()) {
            File staged = new File(dir, relative)
            if(staged.exists())
                return new AbstractMap.SimpleImmutableEntry<String,File>(dir, staged)
        }
        return null
    }

    /**
     * Determine which of a command's outputs must be regenerated because of staging, given
     * the meta data recorded when they were last created. This applies whether or not
     * staging is active in this run, so that:
     *
     * <li>outputs copied from staging are regenerated in a run that does not
     *     stage them from the same directory (dummy data never leaks into a real run)
     * <li>with staging active, if <i>all</i> of the command's outputs have staged counterparts,
     *     an output that did not come from its staged file, or whose staged file has since
     *     been modified, is replaced. When only some outputs are staged the command runs
     *     for real (see {@link #plan}), so real outputs are left alone: otherwise the command
     *     would be re-executed on every run.
     *
     * @param outputs   the outputs of a single command
     * @param props     closure returning the meta data for an output, or null if there is none
     *
     * @return the outputs that are out of date
     */
    @CompileStatic
    static List<PipelineFile> findOutOfDate(List<PipelineFile> outputs, Closure<OutputMetaData> props) {

        // Note: explicit loop rather than collectEntries, which older Groovy 3 versions
        // (eg: 3.0.10) cannot type check under @CompileStatic
        Map<PipelineFile, Map.Entry<String,File>> staged = new HashMap<PipelineFile, Map.Entry<String,File>>()
        boolean allStaged = isEnabled() && !outputs.isEmpty()
        if(isEnabled()) {
            for(PipelineFile o in outputs) {
                Map.Entry<String,File> stagedFile = findStaged(o)
                staged.put(o, stagedFile)
                if(stagedFile == null)
                    allStaged = false
            }
        }

        return outputs.grep { PipelineFile output ->
            String stagedFrom = props(output)?.stagedFrom
            Map.Entry<String,File> stagedFile = staged.get(output)

            if(!allStaged || stagedFile == null) {
                if(stagedFrom) {
                    log.info "Output $output was staged from $stagedFrom but is not staged in this run: out of date"
                    return true
                }
                return false
            }

            if(stagedFrom != stagedFile.key) {
                log.info "Output $output has staged file $stagedFile.value but was not created from it (stagedFrom=$stagedFrom): out of date"
                return true
            }

            if(output.exists() && stagedFile.value.lastModified() > output.toPath().toFile().lastModified()) {
                log.info "Staged file $stagedFile.value is newer than output $output: out of date"
                return true
            }

            return false
        }
    }

    @CompileStatic
    static String canonicalPath(PipelineFile output) {
        Utils.canonicalFileFor(output.path).path
    }

    /**
     * @return the path relative to the run directory, or null if it is not inside the run directory
     */
    @CompileStatic
    static String relativePath(String canonicalPath) {
        Path runDir = Paths.get(Runner.canonicalRunDirectory)
        Path path = Paths.get(canonicalPath)
        if(!path.startsWith(runDir))
            return null
        return runDir.relativize(path).toString()
    }
}
