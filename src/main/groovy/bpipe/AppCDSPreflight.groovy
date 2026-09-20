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

import java.util.logging.Logger

/**
 * Preflight resolver for AppCDS training.
 * <p>
 * Reuses the standard Bpipe CLI parser and config reading to resolve
 * the pipeline script directory and any user-specified libraries, then
 * writes a manifest file that the shell launcher can source to know
 * where to store the dynamic CDS archive.
 */
class AppCDSPreflight {

    private static Logger log = Logger.getLogger("bpipe.AppCDSPreflight")

    /**
     * Run the preflight resolution.
     * <p>
     * Expects the same arguments as a normal {@code bpipe run} invocation,
     * with an additional trailing argument that is the path to write the
     * manifest file to.
     *
     * @param args  command line arguments: [run-args...] <manifest-output-path>
     */
    static void run(List<String> args) {

        if(args.size() < 2) {
            System.err.println("Usage: bpipe appcds train <pipeline> [inputs...] [run-options]")
            System.exit(1)
        }

        // The last argument is the manifest output path, injected by the shell script
        String manifestPath = args[-1]
        List<String> runArgs = args[0..-2]

        // Parse using the same CLI as a normal run
        def cli = Runner.runCli
        Runner.configureRunCli(cli)
        def opt = cli.parse(runArgs)

        if(!opt || !opt.arguments()) {
            System.err.println("ERROR: Could not parse arguments for AppCDS preflight")
            System.exit(1)
        }

        // Resolve the pipeline script to a canonical directory
        String scriptPath = opt.arguments()[0]
        File scriptFile = new File(scriptPath).canonicalFile
        if(!scriptFile.exists()) {
            System.err.println("ERROR: Pipeline script not found: $scriptPath")
            System.exit(1)
        }

        File scriptDir = scriptFile.parentFile

        // Set the script in config so that readUserConfig can find bpipe.config
        // relative to the script, matching normal run behavior
        Config.config.script = scriptFile.path

        // Apply environment if specified, same as Runner.main
        if(opt['e']) {
            Config.config.environment = opt['e']
        }
        else {
            def localEnv = System.getenv('BPIPE_DEFAULT_ENVIRONMENT')
            if(localEnv) {
                Config.config.environment = localEnv
            }
        }

        // Apply parameters if specified
        if(opt['params']) {
            Runner.binding.addParams((List)opt['params'])
        }

        // Read user config (bpipe.config) with environment applied
        try {
            Config.readUserConfig()
        }
        catch(Exception e) {
            log.warning("Failed to read user config during AppCDS preflight: " + e.message)
            // Continue anyway - libs may not be configured
        }

        // Resolve libraries using the same logic as Runner.loadExternalLibs
        List<String> resolvedLibs = []
        if(Config.userConfig.containsKey("libs")) {
            def jars = Config.userConfig.libs
            if(jars instanceof String) {
                jars = jars.tokenize(":")
            }
            for(String jar in jars) {
                File f = new File(jar)
                if(!f.exists()) {
                    File relativeToScript = new File(scriptDir, jar)
                    if(relativeToScript.exists()) {
                        f = relativeToScript
                    }
                }
                if(f.exists()) {
                    resolvedLibs << f.canonicalPath
                }
                else {
                    log.warning("AppCDS preflight: library not found: $jar")
                }
            }
        }

        // Determine archive location
        File appcdsDir = new File(scriptDir, '.bpipe-appcds')
        String archivePath = new File(appcdsDir, 'dynamic.jsa').path

        // Write manifest
        File manifestFile = new File(manifestPath)
        manifestFile.parentFile?.mkdirs()

        manifestFile.text = [
            "APPCDS_PIPELINE_DIR=${shellEscape(scriptDir.path)}",
            "APPCDS_ARCHIVE_PATH=${shellEscape(archivePath)}",
            "APPCDS_RESOLVED_LIBS=${shellEscape(resolvedLibs.join(':'))}",
        ].join('\n') + '\n'

        log.info("AppCDS preflight manifest written to $manifestPath")
        log.info("  pipeline_dir=$scriptDir.path")
        log.info("  archive_path=$archivePath")
        log.info("  libs=${resolvedLibs.join(':')}")
    }

    /**
     * Escape a value for safe inclusion in a shell-sourceable file.
     */
    private static String shellEscape(String value) {
        return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'
    }
}
