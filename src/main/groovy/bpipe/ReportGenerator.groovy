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

import groovy.text.GStringTemplateEngine;
import groovy.text.SimpleTemplateEngine;
import groovy.util.logging.Log;
import groovy.xml.XmlUtil;

@Log
class ReportGenerator {

    public ReportGenerator() {
    }
    
    /**
     * Binding for the generator to use. If null,
     * a default binding containing information about the pipeline is generated
     */
    Map reportBinding = null
    
    /**
     * Instantiates the specified template and generates it from the given pipeline
     *
     * @param templateFile
     */
    void generateFromTemplate(Pipeline pipeline, String templateFileName, String outputDir, String outputFileName) {
        
        File docDir = new File(outputDir)
        if(!docDir.exists()) {
            docDir.mkdirs()
        }
        
        def docStages = [ [] ]
        pipeline.fillDocStages(docStages)
        
        GraphEntry outputGraph = Dependencies.instance.computeOutputGraph(Dependencies.instance.scanOutputFolder())  
        
        // We fill in the outputs for each node that is a pipeline stage
        pipeline.node.breadthFirst().each { Node n ->
            if(n.attributes().type != 'stage')
                return
                
            if(n.attributes().stage.synthetic)
                return
            
            PipelineContext ctx = n.attributes().stage.context
            List<String> outputs = (Utils.box(ctx.@output) + ctx.inferredOutputs).unique()
            n.attributes().outputs = outputs.collect { outputGraph.propertiesFor(it) }
        }
        
        if(reportBinding == null) {
            reportBinding = [
                stages: docStages,
                pipeline: pipeline,
                nodes : pipeline.node
            ]
        }
        else {
            reportBinding.pipeline = pipeline
            reportBinding.docStages = docStages
        }
        
        // Utility to escape basic HTML entities
//        reportBinding.escape = { String obj ->
//            return XmlUtil.escapeXml(String.valueOf(obj))
//        }
        
        reportBinding.commands = CommandManager.executedCommands
        reportBinding.outputGraph =  outputGraph
        reportBinding.escape = Utils.&escape
        reportBinding.utils = new Utils()
         
        if(docStages.any { it.stageName == null })
            throw new IllegalStateException("Should NEVER have a null stage name here")

        File templateFile = resolveTemplateFile(templateFileName)
        File outputFile = new File(docDir, outputFileName)
        
        reportBinding.templatePath = templateFile.canonicalPath
        
        if(templateFileName.endsWith(".groovy")) {
            try {
                reportBinding.outputFile = outputFile
                GroovyShell shell = new GroovyShell(new Binding(reportBinding))
                shell.evaluate(templateFile.text)
            }
            catch(Exception e) {
                e.printStackTrace()
            }
        }
        else {
            generateFromGStringTemplate(pipeline,templateFile,outputDir,outputFile)
        }
    }
    
    void generateFromGStringTemplate(Pipeline pipeline, File templateFile, String outputDir, File outputFile) {
        
        InputStream templateStream = new FileInputStream(templateFile)
        
        // Sadly GStringTemplateEngine is not able to resolve classes
        // that are in the classpath. I am not sure why. However SimpleTemplateEngine
        // does. The advantage of GStringTemplateEngine would be that it can 
        // generate templates that don't fit into memory. It is not really a 
        // problem right now, but perhaps in the long term it would be nice to
        // sort this out.
//        GStringTemplateEngine e = new GStringTemplateEngine()
        SimpleTemplateEngine e  = new SimpleTemplateEngine()
        
        log.info "Generating report to $outputFile.absolutePath"
        templateStream.withReader { r ->
            def template = e.createTemplate(r).make(reportBinding)
            outputFile.text = template.toString()
        }
        templateStream.close()
        println "MSG: Generated report "+ outputFile.absolutePath
    }
    
    static File resolveTemplateFile(String templateFileName) {
        
        // First priority is an absolute path or relative directly to a template file
        File templateFile = new File(templateFileName)
        
        // Look for templates relative to the pipeline script as well
        if(!templateFile.exists()) 
          templateFile = new File(new File(Config.config.script).canonicalFile.parentFile, templateFileName)
        
        // Look in default template locations (such as the stock reports shipped with Bpipe)
        if(!templateFile.exists()) {
                
            for(srcDirName in ["html","templates"]) {
                    
                File srcTemplateDir = new File(System.getProperty("bpipe.home") + "/src/main/$srcDirName/bpipe")
                
                log.info "Searching for template in $srcTemplateDir.canonicalPath"
                    
                templateFile = srcTemplateDir.exists() ?
                      new File(srcTemplateDir, templateFileName)
                    :
                      new File(System.getProperty("bpipe.home") + "/$srcDirName", templateFileName)
                      
                if(templateFile.exists())
                    break
            }
        }
                
        if(!templateFile.exists()) {
            throw new PipelineError("""
                The documentation template you specified (${templateFileName.replaceAll(".html","")}) could not be located. Valid report templates are:
            """.stripIndent() + "\n\t" + templateFile.parentFile.listFiles()*.name.collect{it.replaceAll(".html","")}.join("\n\t"))
        }
        return templateFile
    }
}
