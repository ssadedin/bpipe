package bpipe

import groovy.transform.CompileStatic

@CompileStatic
class FileNameMappingResult {
    String path
    String replaced
}

@CompileStatic
interface FileNameMapper {
    FileNameMappingResult mapFileName(List<String> segments)
}

