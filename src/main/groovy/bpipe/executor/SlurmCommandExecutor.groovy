/*
* Copyright (c) 2012 MCRI, authors
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

/* Modified from TorqueCommandExecutor.groovy for Slurm 
*
* Approach is mimic the wrapper and shell script relationship, and replace 
* Torque commands with Slurm equivalents
*/

package bpipe.executor

import groovy.util.logging.Log
import bpipe.Command;
import bpipe.Config;
import bpipe.ExecutedProcess;
import bpipe.ForwardHost;
import bpipe.PipelineError
import bpipe.Utils

/**
 * Implementation of support for Slurm resource manager.
 * <p>
 * This class adapts the Torque implementation (which is similar) to work
 * with Slurm. The only differences are that some environment variables are
 * set differently in passing through to the bpipe-slurm.sh wrapper.
 *
 * @author simon.sadedin@mcri.edu.au
 * @author andrew.lonsdale@lonsbio.com.au
 * @author slugger70@gmail.com
 */
@Log
class SlurmCommandExecutor extends TorqueCommandExecutor implements CommandExecutor, UtilisationCapturingExecutor {

    public static final long serialVersionUID = 0L

    /**
     * Constructor
     */
    SlurmCommandExecutor() {
        super(new File(System.getProperty("bpipe.home") + "/bin/bpipe-slurm.sh"))

        // The pooled status polling only works for PBS Torque
        this.useLegacyJobPolling = true
    }

    @Override
    void setEnvironment(Map env) {
        super.setEnvironment(env)

        if(config?.memory)
            env.MEMORY = String.valueOf(config.memory)

        // jobtype since queues (parition in slurm) may not determine computation mode
        if(config?.jobtype) {
            log.info "Using jobtype: $config?.jobtype"
            env.JOBTYPE = config.jobtype
        }

        //modules since we may need to load modules before the command... - Simon Gladman (slugger70) 2014
        if(config?.modules) {
            log.info "Using modules: $config?.modules"
            env.MODULES = config.modules
        }


    }

    @Override
    String getErrorWarning(int exitCode) {
       switch(exitCode) {
           case 999:
               return "Job $commandId (command $command.id) in stage $command.name was cancelled"
           case 998:
               return "Job commandId (command $command.id) timed out (exceeded walltime) in stage $command.name"
           case 997:
               return "Job $commandId (command $command.id) exceeded memory limit in stage $command.name"
       }
    }

    void cleanup() {
        this.stopForwarding()
        // slurm12 is stdout and stderr
        File slurm12= new File("slurm-"+this.commandId+".out")
        if(slurm12.exists())
            slurm12.delete()
    }

    /**
     * The slurm script / system produces a file slurm-SLURMID.out with both stderr and
     * stdout. We don't want
     * these to be considered as result files from jobs so return a mask
     * that screens them out.
     */
    @Override
    List<String> getIgnorableOutputs() {
        return ["slurm-[0-9]*.out"]
    }

    /**
     * Capture utilisation data from SLURM's sacct accounting system.
     * <p>
     * Polls {@code sacct -j <jobId> --parsable2 --noheader} with a bounded
     * retry loop. Parses the {@code .batch} step row which holds the
     * authoritative MaxRSS for the actual process. Returns as soon as
     * "good enough" data is available (at least one of CPU time or peak RSS
     * is non-null and non-zero), or when the configured timeout expires.
     *
     * @return captured utilisation, or null if sacct is unavailable or
     *         returned no usable data within the timeout
     */
    @Override
    CommandUtilisation captureUtilisation() {
        if(!this.commandId) {
            log.warning "Cannot capture utilisation: no SLURM job ID available"
            return null
        }

        def utilisationConfig = Config.userConfig.utilisation
        int pollIntervalMs = ((utilisationConfig?.pollIntervalSeconds ?: 2) as int) * 1000
        int maxWaitMs = ((utilisationConfig?.maxWaitSeconds ?: 30) as int) * 1000

        long deadline = System.currentTimeMillis() + maxWaitMs
        CommandUtilisation partial = null

        while(true) {
            try {
                CommandUtilisation result = trySacct()
                if(result != null) {
                    // "good enough" = at least one of CPU time or peak RSS is populated
                    boolean goodEnough =
                        (result.cpuSecondsTotal != null && result.cpuSecondsTotal > 0) ||
                        (result.maxRssBytes != null && result.maxRssBytes > 0)
                    if(goodEnough) {
                        return result
                    }
                    partial = result // record even if sparse
                }
            }
            catch(Exception e) {
                log.warning "Error polling sacct for job ${commandId}: ${e.message}"
            }

            if(System.currentTimeMillis() >= deadline) {
                break
            }

            Thread.sleep(Math.min(pollIntervalMs, Math.max(0L, deadline - System.currentTimeMillis())) as long)

            if(System.currentTimeMillis() >= deadline) {
                break
            }
        }

        log.info "sacct polling for job ${commandId} reached timeout; returning ${partial ? 'partial' : 'null'} result"
        return partial
    }

    /**
     * Execute a single sacct query and parse the result.
     *
     * @return parsed utilisation from the .batch row, or null if no usable row found
     */
    CommandUtilisation trySacct() {
        List<String> cmd = [
            'sacct', '-j', this.commandId,
            '--format=JobID,State,Elapsed,TotalCPU,MaxRSS,MaxVMSize',
            '--parsable2', '--noheader'
        ]

        ExecutedProcess result = Utils.executeCommand(
            [timeout: 10000L, source: 'sacct'] as Map,
            cmd as List<Object>
        )

        if(result.exitValue != 0) {
            log.warning "sacct exited with code ${result.exitValue} for job ${commandId}: ${result.err}"
            return null
        }

        String output = result.out.toString().trim()
        if(!output) {
            log.info "sacct returned empty output for job ${commandId}"
            return null
        }

        // Look for the .batch row — it holds authoritative per-process stats
        // If no .batch row, fall back to the main job row
        String batchRow = null
        String mainRow = null
        for(String line : output.readLines()) {
            if(line.contains('.batch')) {
                batchRow = line
                break
            }
            else if(mainRow == null && !line.contains('.extern') && !line.contains('.')) {
                // Main job row: jobId without a dot suffix
                mainRow = line
            }
        }

        String rowToParse = batchRow ?: mainRow
        if(!rowToParse) {
            log.info "sacct output for job ${commandId} contained no parsable rows"
            return null
        }

        return parseSacctRow(rowToParse)
    }

    /**
     * Parse a single pipe-delimited sacct row.
     * <p>
     * Expected field order (matching the --format above):
     * JobID|State|Elapsed|TotalCPU|MaxRSS|MaxVMSize
     */
    CommandUtilisation parseSacctRow(String row) {
        String[] fields = row.split('\\|', -1)
        if(fields.length < 4) {
            log.warning "sacct row has fewer than 4 fields: ${row}"
            return null
        }

        CommandUtilisation u = new CommandUtilisation()
        u.source = 'sacct'
        u.capturedAtMs = System.currentTimeMillis()

        // Field 1: State
        String stateField = fields[1]?.trim()
        if(stateField) {
            u.state = stateField
        }

        // Field 2: Elapsed — wall clock time [D-]HH:MM:SS
        String elapsedField = fields[2]?.trim()
        if(elapsedField) {
            Long elapsedSec = parseTimeToSeconds(elapsedField)
            if(elapsedSec != null && elapsedSec > 0) {
                u.elapsedSeconds = elapsedSec
            }
        }

        // Field 3: TotalCPU — total CPU time [D-]HH:MM:SS[.fraction]
        String cpuField = fields[3]?.trim()
        if(cpuField) {
            Long cpuSec = parseTimeToSeconds(cpuField)
            // Some sites always report 00:00:00 — treat as null, not zero
            if(cpuSec != null && cpuSec > 0) {
                u.cpuSecondsTotal = cpuSec
            }
        }

        // Derived: coresUsed
        if(u.cpuSecondsTotal != null && u.elapsedSeconds != null && u.elapsedSeconds > 0) {
            u.coresUsed = u.cpuSecondsTotal / (double)u.elapsedSeconds
        }

        // Field 4: MaxRSS (e.g. "6475956K", "1.5G", empty)
        if(fields.length > 4) {
            String rssField = fields[4]?.trim()
            if(rssField) {
                Long rssBytes = parseMemoryToBytes(rssField)
                if(rssBytes != null && rssBytes > 0) {
                    u.maxRssBytes = rssBytes
                }
            }
        }

        // Field 5: MaxVMSize
        if(fields.length > 5) {
            String vmemField = fields[5]?.trim()
            if(vmemField) {
                Long vmemBytes = parseMemoryToBytes(vmemField)
                if(vmemBytes != null && vmemBytes > 0) {
                    u.maxVmemBytes = vmemBytes
                }
            }
        }

        return u
    }

    /**
     * Parse a SLURM time string of the form {@code [D-]HH:MM:SS[.fraction]} to seconds.
     * <p>
     * Examples: {@code "01:23:45"} → 5025, {@code "1-02:30:00"} → 95400,
     * {@code "00:00:00.123"} → 0
     *
     * @return seconds, or null if the input is empty or unparseable
     */
    static Long parseTimeToSeconds(String timeStr) {
        if(!timeStr || timeStr.isEmpty())
            return null

        try {
            long days = 0
            String hms = timeStr

            // Handle optional D- prefix
            int dashIdx = hms.indexOf('-')
            if(dashIdx >= 0) {
                days = Long.parseLong(hms.substring(0, dashIdx))
                hms = hms.substring(dashIdx + 1)
            }

            // Strip fractional seconds if present
            int dotIdx = hms.indexOf('.')
            if(dotIdx >= 0) {
                hms = hms.substring(0, dotIdx)
            }

            String[] parts = hms.split(':')
            if(parts.length < 3) {
                // MM:SS format (sometimes used for TotalCPU)
                if(parts.length == 2) {
                    long mins = Long.parseLong(parts[0])
                    long secs = Long.parseLong(parts[1])
                    return days * 86400 + mins * 60 + secs
                }
                return null
            }

            long hours = Long.parseLong(parts[0])
            long mins = Long.parseLong(parts[1])
            long secs = Long.parseLong(parts[2])
            return days * 86400 + hours * 3600 + mins * 60 + secs
        }
        catch(NumberFormatException e) {
            log.warning "Failed to parse SLURM time string '${timeStr}': ${e.message}"
            return null
        }
    }

    /**
     * Parse a SLURM memory string (e.g. "6475956K", "1.5G", "900M") to bytes.
     * <p>
     * Supported suffixes: K (KiB), M (MiB), G (GiB), T (TiB). Case-insensitive.
     * If no suffix, the value is assumed to be in bytes.
     *
     * @return bytes, or null if the input is empty or unparseable
     */
    static Long parseMemoryToBytes(String memStr) {
        if(!memStr || memStr.isEmpty())
            return null

        try {
            String upper = memStr.trim().toUpperCase()
            char lastChar = upper.charAt(upper.length() - 1)

            if(Character.isLetter(lastChar)) {
                String numPart = upper.substring(0, upper.length() - 1)
                double value = Double.parseDouble(numPart)
                switch(lastChar) {
                    case 'K': return (long)(value * 1024L)
                    case 'M': return (long)(value * 1024L * 1024L)
                    case 'G': return (long)(value * 1024L * 1024L * 1024L)
                    case 'T': return (long)(value * 1024L * 1024L * 1024L * 1024L)
                    default:
                        log.warning "Unknown memory unit suffix '${lastChar}' in '${memStr}'"
                        return null
                }
            }
            else {
                // No suffix — assume bytes
                return Long.parseLong(upper)
            }
        }
        catch(NumberFormatException e) {
            log.warning "Failed to parse SLURM memory string '${memStr}': ${e.message}"
            return null
        }
    }

    String toString() {
        "Slurm Job [" + "Command Id: $commandId " + (config?"Configuration: $config":"") + "]"
    }
}
