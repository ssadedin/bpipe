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
package bpipe

import java.util.Map;
import groovy.util.logging.Log;

import static PipelineEvent.*

@Log
class ReportStatisticsListener implements PipelineEventListener {
    
    String reportName
    
    String outputFileName
    
    String outputDir
    
    boolean notification = false
    
    ReportStatisticsListener() {
        this("index","index.html")
    }
    
    ReportStatisticsListener(String reportName, String outputFileName, String outputDir="doc", boolean notification=false) {
        this.reportName = reportName
        this.outputFileName = outputFileName
        this.outputDir = outputDir
        this.notification = notification
        EventManager.instance.addListener(PipelineEvent.STAGE_STARTED, this)
        EventManager.instance.addListener(PipelineEvent.STAGE_COMPLETED, this)
        EventManager.instance.addListener(PipelineEvent.COMMAND_CHECK, this)
        EventManager.instance.addListener(PipelineEvent.FINISHED, this)
    }

	@Override
	public void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details) {
		
		PipelineContext ctx = details?.stage?.context
        if(!ctx)
            ctx = details.ctx
		
		if(!ctx && (eventType in [STAGE_STARTED,STAGE_COMPLETED]))	{
			log.warning("Pipeline stage or context missing from details provided to statistics event")
			return
		}
		
		switch(eventType) {
			
			case STAGE_STARTED:
				log.info "Annotating stage start time $ctx.stageName"
				ctx.doc startedAt: new Date()
			break
			
			case STAGE_COMPLETED:
				log.info "Annotating stage end time $ctx.stageName"
				ctx.doc finishedAt: new Date(), elapsedMs: (System.currentTimeMillis() - ctx.documentation.startedAt.time)
			break
            
            case COMMAND_CHECK:
                log.info "Intercepted command check for command $details.command"
                def toolsDiscovered = ToolDatabase.instance.probe(details.command)
                
                // If no tools discovered, document the command anyway
                if(!toolsDiscovered) {
                  ctx.doc(["undocumented": details.command])
                }
                else
                // if some tools are already discovered, merge rather than overwriting all of them
                if(ctx.documentation.tools)
                  ctx.documentation.tools += toolsDiscovered
                else
                  ctx.doc(["tools" : toolsDiscovered])
                break
                
            case FINISHED:
                generateCustomReport(details.pipeline)
                if(notification)
                    EventManager.instance.signal(PipelineEvent.REPORT_GENERATED, "Report generated: $reportName", [reportListener: this])
                break
		}
	}
    
    def generateCustomReport(Pipeline pipeline) {
        try {
          if(!pipeline.documentation.title)
            pipeline.documentation.title = "Pipeline Report"
          new ReportGenerator().generateFromTemplate(pipeline, reportName + ".html", this.outputDir, this.outputFileName)
        }
        catch(PipelineError e) {
            System.err.println "\nA problem occurred generating your report:"
            System.err.println e.message + "\n"
        }
    }
}
