package bpipe

import static org.junit.Assert.*

import org.junit.Test

import bpipe.storage.LocalPipelineFile

class GraphEntryTest {

    static OutputMetaData out(String name) {
        OutputMetaData o = new OutputMetaData()
        o.outputFile = new LocalPipelineFile(name)
        o.outputPath = name
        o.canonicalPath = new File(name).canonicalPath
        return o
    }

    /**
     * The closure based search that entryForCanonicalPath used before
     * findByCanonicalPath replaced it
     */
    static GraphEntry legacySearch(GraphEntry root, String canonicalPath) {
        root.findBy { OutputMetaData p ->
           !p.outputFile.is(null) && (GraphEntry.canonicalPathFor(p) == canonicalPath)
        }
    }

    @Test
    void 'findByCanonicalPath matches the closure based search'() {

        // root => a => (b, c) ; b => d ; c => d  (d reachable twice: a DAG, not a tree)
        // plus an entry whose output has no output file, and a multi-output entry
        GraphEntry root = new GraphEntry(values: [])
        GraphEntry a = new GraphEntry(values: [out('a.txt')])
        GraphEntry b = new GraphEntry(values: [out('b.txt'), out('b2.txt')])

        OutputMetaData noFile = out('nofile.txt')
        noFile.outputFile = null
        GraphEntry c = new GraphEntry(values: [noFile, out('c.txt')])
        GraphEntry d = new GraphEntry(values: [out('d.txt')])

        root.children << a
        a.children.addAll([b, c])
        b.children << d
        c.children << d

        List<String> paths = ['a.txt','b.txt','b2.txt','c.txt','d.txt','nofile.txt','missing.txt'].collect {
            new File(it).canonicalPath
        } + [null]

        for(String p in paths) {
            assert root.findByCanonicalPath(p).is(legacySearch(root, p)) : "Different result for $p"
        }

        assert root.findByCanonicalPath(new File('b2.txt').canonicalPath).is(b)
        assert root.findByCanonicalPath(new File('d.txt').canonicalPath).is(d)
        assert root.findByCanonicalPath(new File('nofile.txt').canonicalPath) == null
        assert root.findByCanonicalPath(new File('missing.txt').canonicalPath) == null
    }

    @Test
    void 'findBy treats only false as a non-match'() {
        GraphEntry root = new GraphEntry(values: [])
        GraphEntry a = new GraphEntry(values: [out('a.txt')])
        root.children << a

        assert root.findBy { false } == null
        assert root.findBy { true }.is(a)
        assert root.findBy { null }.is(a)  // matches legacy semantics of c(value) != false
    }
}
