

withConfig(configuration) {
    configuration.setScriptBaseClass('bpipe.BpipeScriptBase')
    configuration.addCompilationCustomizers(new org.codehaus.groovy.control.customizers.ImportCustomizer().tap {
        addImport('Filter', 'Filter')
        addImport('Transform', 'Transform')
    })
}
