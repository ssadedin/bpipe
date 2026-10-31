package bpipe.staging

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic

/**
 * Finds companion files (typically indexes) that tools expect to find next to an input,
 * such as <code>x.bam.bai</code> or <code>x.bai</code> for <code>x.bam</code>, and
 * <code>ref.fa.fai</code> or <code>ref.dict</code> for <code>ref.fa</code>.
 * <p>
 * A companion is a file that exists in the same directory, named either
 * <code>&lt;name&gt;&lt;ext&gt;</code> or <code>&lt;name without its last extension&gt;&lt;ext&gt;</code>
 * for one of a fixed list of extensions.
 */
@CompileStatic
class Companions {

    static List<Path> find(Path file, List<String> extensions) {

        String name = file.fileName.toString()

        List<String> bases = [name]
        int dot = name.lastIndexOf('.')
        if(dot > 0)
            bases.add(name.substring(0, dot))

        List<Path> result = []
        for(String base in bases) {
            for(String ext in extensions) {
                Path candidate = file.resolveSibling(base + ext)
                if(candidate != file && Files.isRegularFile(candidate) && !result.contains(candidate))
                    result.add(candidate)
            }
        }
        return result
    }
}
