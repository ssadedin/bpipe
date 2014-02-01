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
        
        if(reportBinding == null) {
            reportBinding = [
                stages: docStages,
                pipeline: pipeline
            ]
        }
         
        if(docStages.any { it.stageName == null })
            throw new IllegalStateException("Should NEVER have a null stage name here")

        // First priority is an absolute path or relative directly to a template file
        File templateFile = new File(templateFileName)
        
        // Look for templates relative to the pipeline script as well
        if(!templateFile.exists()) 
          templateFile = new File(new File(Config.config.script).parentFile, templateFileName)
        
        // Look in default template locations (such as the stock reports shipped with Bpipe)
        if(!templateFile.exists()) {
            
            File srcTemplateDir = new File(System.getProperty("bpipe.home") + "/src/main/html/bpipe")
            
            templateFile = srcTemplateDir.exists() ?
                  new File(srcTemplateDir, templateFileName)
                :
                  new File(System.getProperty("bpipe.home") + "/html", templateFileName)
                  
    
            if(!templateFile.exists()) {
                throw new PipelineError("""
                    The documentation template you specified (${templateFileName.replaceAll(".html","")}) could not be located. Valid report templates are:
                """.stripIndent() + "\n\t" + templateFile.parentFile.listFiles()*.name.collect{it.replaceAll(".html","")}.join("\n\t"))
            }
        }
            
        InputStream templateStream = new FileInputStream(templateFile)
        GStringTemplateEngine e = new GStringTemplateEngine()
        File outputFile = new File(docDir, outputFileName)
        templateStream.withReader { r ->
            def template = e.createTemplate(r).make(reportBinding)
            outputFile.text = template.toString()
        }
        templateStream.close()
        println "MSG: Generated report "+ outputFile.absolutePath
    }
}
