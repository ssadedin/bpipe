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

import groovy.util.logging.Log
import groovyx.gpars.GParsPool

import bpipe.storage.StorageLayer

/**
 * {@link OutputMetaDataStore} implementation that persists each record as an
 * individual Java .properties file under <code>.bpipe/outputs/</code>.
 * <p>
 * This is the original Bpipe storage format and serves as both the default
 * legacy fallback and the migration source when upgrading to SQLite.
 */
@Log
class PropertyFileOutputMetaDataStore implements OutputMetaDataStore {

    private static final Set<String> EXCLUDE_SAVE_PROPERTIES = [
        "upToDate",
        "maxTimeStamp",   // intentional capitalisation matches original; maxTimestamp (lowercase) IS saved
        "class"
    ] as Set

    // -------------------------------------------------------------------------
    // OutputMetaDataStore interface
    // -------------------------------------------------------------------------

    @Override
    void save(OutputMetaData omd) {
        Properties props = new Properties()

        for(k in omd.properties.keySet()) {
            if(k in EXCLUDE_SAVE_PROPERTIES)
                continue
            props[k] = String.valueOf(omd.getProperty(k))
        }

        // Override with properly-formatted values
        props.inputs    = omd.inputs.join(",")
        props.cleaned   = String.valueOf(omd.cleaned)
        props.outputFile = String.valueOf(omd.outputFile)
        props.storage   = omd.outputFile.storage.name

        // TODO: the whole point of storing the timestamp is to get better resolution than
        // is offered by the file system. On the other hand, if the file exists and has a timestamp,
        // shouldn't we believe that over what we recorded? Perhaps it should be the latest of either?
        if(omd.outputFile.exists())
            props.timestamp = String.valueOf(omd.outputFile.lastModified())
        else if(!omd.timestamp)
            props.timestamp = "0"
        else
            props.timestamp = String.valueOf(omd.timestamp)

        props.createTimeMs = omd.createTimeMs ? String.valueOf(omd.createTimeMs) : "0"
        props.stopTimeMs   = omd.stopTimeMs   ? String.valueOf(omd.stopTimeMs)   : "0"

        File propFile = getPropertyFileFor(omd)
        log.info "Saving output file details to file $propFile for command " + Utils.truncnl(omd.command, 20)
        propFile.withOutputStream { ofs ->
            props.save(ofs, "Bpipe Output File Meta Data")
        }
    }

    @Override
    List<OutputMetaData> loadAll() {
        int concurrency = (int)(Config.userConfig?.getOrDefault('outputScanConcurrency', 5) ?: 5)
        List<OutputMetaData> result = []
        Utils.time("Output folder scan (concurrency=$concurrency)") {

            File outputsDir = new File(OutputMetaData.OUTPUT_METADATA_DIR)
            File[] allFiles = outputsDir.listFiles()
            if(allFiles == null)
                return result

            List<File> files = (List<File>)allFiles.findAll { isOutputMetaFile(it) }
            if(files.isEmpty())
                return result

            GParsPool.withPool(concurrency) {
                result.addAll(
                    files.collectParallel { File f ->
                        OutputMetaData omd = new OutputMetaData()
                        try {
                            if(loadFromFile(omd, f))
                                return omd
                        }
                        catch(Exception e) {
                            log.warning("Failed to read output metadata from $f: ${e.message}")
                        }
                        return null
                    }.grep { it != null }.sort { it.timestamp }
                )
            }
        }
        return result
    }

    @Override
    boolean exists(OutputMetaData omd) {
        return getPropertyFileFor(omd).exists()
    }

    @Override
    void load(OutputMetaData omd) {
        File f = getPropertyFileFor(omd)
        if(f.exists())
            loadFromFile(omd, f)
    }

    @Override
    void flush() {
        // Property files are written synchronously; nothing to flush.
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Populate {@code omd} from the given properties file.
     * Returns {@code true} on success; {@code false} if the file is missing a
     * required field (the caller should discard the partially-populated object).
     */
    private boolean loadFromFile(OutputMetaData omd, File f) {
        log.info "Reading property file $f"

        Properties p = new Properties()
        new FileInputStream(f).withStream { p.load(it) }

        omd.outputPath = p.outputPath

        if(p.inputs)
            omd.inputs = p.inputs.split(",") as List

        omd.cleaned = p.containsKey('cleaned') ? Boolean.parseBoolean(p.cleaned) : false

        if(!p.outputFile) {
            log.warning("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println("Properties are: $p")
            return false
        }

        omd.storage    = StorageLayer.create(p.storage ?: 'local')
        omd.outputFile = new PipelineFile(omd.outputPath, omd.storage)

        // Normalise slashes for Cygwin compatibility
        omd.outputPath = String.valueOf(omd.outputFile).replace('\\', "/")

        // Prefer live filesystem timestamp when file exists
        if(omd.outputFile.exists())
            omd.timestamp = omd.outputFile.lastModified()
        else if(p.timestamp != null)
            omd.timestamp = Long.parseLong(p.timestamp)

        // Cached canonical path is only valid if the run directory hasn't changed
        if(!p.containsKey("basePath") || (p["basePath"] != Runner.runDirectory))
            omd.canonicalPath = Utils.canonicalFileFor(omd.outputFile.path)
        else
            omd.canonicalPath = p.canonicalPath

        omd.basePath     = p.basePath
        omd.preserve     = Boolean.parseBoolean(p.preserve)
        omd.intermediate = Boolean.parseBoolean(p.intermediate)
        omd.commandId    = p.commandId ?: "-1"
        omd.startTimeMs  = (p.startTimeMs  ?: 0).toLong()
        omd.createTimeMs = (p.createTimeMs ?: 0).toLong()
        omd.stopTimeMs   = (p.stopTimeMs   ?: 0).toLong()

        if(p.containsKey("tools"))
            omd.tools = p.tools

        if(p.containsKey("branchPath"))
            omd.branchPath = p.branchPath

        if(p.containsKey("stageName"))
            omd.stageName = p.stageName

        if(p.containsKey("stageId"))
            omd.stageId = p.stageId

        omd.fingerprint = p.fingerprint
        omd.accompanies = p.accompanies
        omd.command     = p.command
        omd.stub        = p.containsKey('stub') ? Boolean.parseBoolean(p.stub) : false

        return true
    }

    /**
     * Compute the .properties file path for the given output metadata object.
     * Matches the naming convention from the original OutputMetaData class.
     */
    static File getPropertyFileFor(OutputMetaData omd) {
        File outputsDir = new File(OutputMetaData.OUTPUT_METADATA_DIR)
        if(!outputsDir.exists())
            outputsDir.mkdirs()

        String outputPath = new File(omd.outputPath).path

        // Trim leading run-directory prefix for a shorter, portable filename
        if(outputPath.indexOf(Runner.canonicalRunDirectory) == 0)
            outputPath = outputPath.substring(Runner.canonicalRunDirectory.size() + 1)

        if(outputPath.startsWith("./"))
            outputPath = outputPath.substring(2)

        int maxLen = (Config.userConfig?.getOrDefault('maxFileNameLength', 2048) ?: 2048) as int
        String pathElement = outputPath.replace('/', "_").replace('\\', '_')
        String fileName = "${omd.stageName}.${pathElement}.properties"
        if(fileName.size() > maxLen)
            fileName = "${omd.stageName}.${pathElement.substring(0, maxLen - omd.stageName.size() - 60)}.${Utils.sha1(pathElement)}.properties"

        return new File(outputsDir, fileName)
    }

    /**
     * Return true if the given file is a valid output metadata properties file.
     * Skips hidden files, directories, and the known serialized-graph cache files.
     */
    static boolean isOutputMetaFile(File file) {
        !file.name.startsWith('.') &&
        !file.isDirectory() &&
        !file.name.equals('outputGraph.ser') &&
        !file.name.equals('outputGraph2.ser')
    }
}
