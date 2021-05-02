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
package bpipe.executor

import java.util.concurrent.ExecutorService
import groovy.util.logging.Log

import org.gridgain.grid.Grid
import org.gridgain.grid.GridConfigurationAdapter
import org.gridgain.grid.typedef.G

import bpipe.Config
import bpipe.EventManager
import bpipe.PipelineEvent
import bpipe.PipelineEventListener

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@Singleton
@Log
class GridgainProvider implements ExecutorServiceProvider {

    private boolean started

    @Lazy
    Grid grid = {

        /*
         * try to look for a bpipe-gridgain.xml file on the current path
         */

        String configFileName = Config.userConfig.getOrDefault("gridgain.conf.file", "./gridgain.xml")

        File gg = new File(configFileName)
        if( gg.exists() ) {
            log.info( "Launching GridGain with user configuration: '${gg.absolutePath}'" )
            G.start(gg.absolutePath)
        }
        else {
            log.info("Launching GridGain with DEFAULT configuration")
            GridConfigurationAdapter cfg = new GridConfigurationAdapter()
            cfg.setDaemon(true)
            G.start(cfg)
        }


        /*
        * Add an event listener that will shutdown the GridGain executor when the pipeline finishes
        */
        EventManager.instance.addListener(PipelineEvent.FINISHED, new PipelineEventListener() {
            @Override
            void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details) {
                if( started ) {
                    log.info("Shutting down GridGain executor")
                    G.stop(true)
                }
            }
        })


        /*
         * Start GridGain
         */
        def result = G.grid();
        started = true
        return result

    } ()

    @Override
    def getName() { "GridGain" }

    @Override
    ExecutorService getExecutor() {
        return grid.executor()
    }
}
