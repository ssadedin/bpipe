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

/**
 * {@link OutputMetaDataStore} implementation that persists each record as an
 * individual Java .properties file under <code>.bpipe/outputs/</code>.
 * <p>
 * This is the original Bpipe storage format and serves as both the default
 * backend and the legacy migration source when upgrading to SQLite.
 */
@Log
class PropertyFileOutputMetaDataStore implements OutputMetaDataStore {

    @Override
    void save(OutputMetaData p) {
        p.save()
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
                        OutputMetaData.fromFile(f)
                    }.grep { it != null }.sort { it.timestamp }
                )
            }
        }
        return result
    }

    @Override
    boolean exists(OutputMetaData p) {
        return p.exists()
    }

    @Override
    void load(OutputMetaData p) {
        if(p.exists()) {
            p.read()
        }
    }

    @Override
    void flush() {
        // Property files are written synchronously; nothing to flush.
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
