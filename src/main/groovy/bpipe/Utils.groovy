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

import groovy.time.TimeCategory;

import groovy.transform.CompileStatic;
import groovy.util.logging.Log;
import groovy.xml.XmlUtil;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern
import org.codehaus.groovy.runtime.StackTraceUtils

import bpipe.storage.StorageLayer

class ExecutedProcess {
    Appendable err
    Appendable out
    int exitValue
}

/**
 * Miscellaneous internal utilities used by Bpipe
 * 
 * @author ssadedin@mcri.edu.au
 */
class Utils {
    
    public static Logger log = Logger.getLogger("bpipe.Utils")
    
    
    /**
     * Convert a list of heterogenous objects to Path objects
     * 
     * @param fileObjs
     * @return
     */
    @CompileStatic
    static List<Path> toPaths(def fileObjs) {
        (List<Path>)fileObjs.grep { it != null }.collect {  f ->
            if(f instanceof File )
                return ((File)f).toPath()
                
            if(f instanceof GlobPipelineFile) {
                GlobPipelineFile gpf = ((GlobPipelineFile)f)
                return gpf.toPaths()
            }
            else
            if(f instanceof PipelineFile) {
                PipelineFile pf = ((PipelineFile)f)
                return pf.toPath() 
            }
            
            return new File(String.valueOf(f)).toPath()
        }.flatten()
    }
    
    @CompileStatic
    static List<PipelineFile> resolveToDefaultStorage(def inputs) {
        (List<PipelineFile>)Utils.box(inputs).collect { inp ->
                if(inp instanceof PipelineFile)
                    return inp
                else {
                    return new PipelineFile(String.valueOf(inp),StorageLayer.getDefaultStorage())
                }
        }        
    }
    
    /**
     * Returns a list of output files that appear to be out of date
     * because they are either missing or older than one of the input
     * files
     * 
     * @param outputs   a collection of file-like objects (strings, File, PipelineFile)
     * @param inputs    a collection of file-like objects (strings, File, PipelineFile)
     * @return
     */
    @CompileStatic
    static List<Path> findOlder(List<PipelineFile> outputsToCheck, List<PipelineFile> inputs) {
        
        assert inputs instanceof List 
        assert outputsToCheck instanceof List 
        
        if(outputsToCheck.any { it instanceof String })
            assert false : "Output specified as raw string: internal error"
        
        List<Path> inputPaths = toPaths(inputs)
        
        // Remove any directories appearing as inputs - their timestamps change whenever
        // any file in the dir changes        
        inputPaths = inputPaths.grep { Path p -> !Files.isDirectory(p) }
        
        // Some pipeline stages don't expect any outputs
        // Fixing issue 44 - https://code.google.com/p/bpipe/issues/detail?id=44
        if( !outputsToCheck || !inputPaths )
            return []

        TreeMap inputFileTimestamps = new TreeMap()
        for(Path inputPath in inputPaths) {
            // NOTE: it doesn't actually matter if two have the same 
            // timestamp for the purposes of our algorithm
            if(Files.exists(inputPath))
                inputFileTimestamps[Files.getLastModifiedTime(inputPath).toMillis()] = inputPath
            else
                // TODO - CLOUD - this is replicating old behavior for refactoring to 
                // use nio Paths, but is it actually correct? It will make the input look very old
                // and therefore never trigger dependency update
                inputFileTimestamps[0L] = inputPath 
        }
        
        final long maxInputTimestamp = (long)(inputFileTimestamps.lastKey()?:0L)
        final long minInputTimestamp = (long)(inputFileTimestamps.firstKey()?:0L)
        
        final boolean logTimestamps = inputPaths.size()*outputsToCheck.size() < 5000 // 5k lines in the log from 1 loop is too much

        List<Path> outputPaths = toPaths(outputsToCheck)
        List<Path> result = outputPaths.grep { Path outFile ->
            isOlder(outFile, inputFileTimestamps, maxInputTimestamp)
        } + outputsToCheck.grep { PipelineFile pf -> !pf.exists() }*.toPath()
        
        return result.unique { it.toString() }
    }
    
    @CompileStatic
    static boolean isOlder(Path outFile, TreeMap<Long,Path> inputPaths, long maxInputTimestamp) {
        
        log.info "===== Check $outFile ====="
        if(Files.notExists(outFile)) {
            log.info "file doesn't exist: $outFile"
            return true
        }
            
        long outputTimestamp = Files.getLastModifiedTime(outFile).toMillis()
        
        if(maxInputTimestamp < outputTimestamp) {
            log.info "Output newer than all inputs (quick check: $maxInputTimestamp vs $outputTimestamp)"
            return false
        }
                
        if(inputPaths.size()<5)
            log.info "Checking $outFile against inputs ${inputPaths.values()}"
        else
            log.info "Checking $outFile against ${inputPaths.size()} inputs" 
                    
        SortedMap<Long,Path> newerInputs = inputPaths.tailMap((Long)(outputTimestamp+1))
        if(!newerInputs.isEmpty())
            log.info "${newerInputs.size()} are newer than ${outFile} starting with ${newerInputs.iterator().next()}}"
        else
            log.info "No inputs are newer than $outFile"
        
        return !newerInputs.isEmpty()
    }
    
    @CompileStatic
    static boolean fileExists(PipelineOutput o) {
        fileExists(new File(o.toString()))
    }
    
    @CompileStatic
    static boolean fileExists(PipelineInput i) {
        fileExists(new File(i.toString()))
    }
    
    @CompileStatic
    static boolean fileExists(File f) {
        
       if(f.exists())
           return true
           
       if(!f.exists()) {
           log.info "File $f does not appear to exist: listing directory to flush file system"
           try { f.absoluteFile.parentFile.listFiles() } catch(Exception e) { log.warning("Failed to list files of parent directory of $f"); }
           if(f.exists())
               log.info("File $f revealed by listing directory")
       } 
       return f.exists()
    }
    
    /**
     * Attempt to delete all of the specified outputs, if any
     * 
     * @param outputs   string or collection of strings representing 
     *                  names of files to be deleted
     * @return List of outputs that could not be cleaned up
     */
    static List<String> cleanup(def outputs) {
        if(!outputs)
            return
            
        List<String> failed = []
        box(outputs).collect { new File(it) }.each { File f -> 
            
            if(!fileExists(f)) {
                log.info "Not cleaning up file $f because it does not exist"
                return
            }
            
            // it.delete() 
            File trashDir = new File(".bpipe/trash")
            if(!trashDir.exists())
                trashDir.mkdirs()
                
            File dest = new File(trashDir, f.name)
            if(Runner.testMode) {
                println "[TEST MODE] Would clean up file $f to $dest" 
                return
            }

            int count = 1;
            while(dest.exists()) {
                dest = new File(trashDir, f.name + ".$count")
                ++count
            }
            
            if(!f.renameTo(dest) && f.exists()) {
                println "WARNING: failed to clean up file $f"
                log.severe("Unable to cleanup file ${f.absolutePath} by moving it to ${dest.absolutePath}. Creating dirty file record.")
                failed.add(f.canonicalFile.absolutePath)
            }
            else {
                println "Cleaned up file $f to $dest" 
                log.info("Cleaned up file ${f.absolutePath} by moving it to ${dest.absolutePath}")
            }
        }
        return failed
    }
    
    /**
     * Return true if the specified object is a collection or
     * array, false otherwise.
     */
    static boolean isContainer(def obj) {
        return (obj != null) && (obj instanceof Collection || ((obj.class != null) && obj.class.isArray()))
    }
    
    /**
     * Normalize a single input and array into a collection, 
     * return existing collections as is
     */
    @CompileStatic
    static List box(Object outputs) {
        
        if(outputs == null)
            return []
        
        if(outputs instanceof Collection)
            return outputs as List
        
        if(outputs.class.isArray())    
            return outputs as List
            
        return [outputs]
    }
    
    /**
     * Return the given inputs as an individual object if they are 
     * a collection with only 1 entry, otherwise just return the object
     */
    static unbox(inputs) {
        return isContainer(inputs) && inputs.size() == 1 ? inputs[0] : inputs
    }
    
    static first(inputs) {
        if(inputs == null)
            return null
            
        if(isContainer(inputs))
            return inputs.size() > 0 ? inputs[0] : null
            
        // Plain object
        return inputs
    }
    
    /**
     * Truncate the input intelligently at the first new line or at most 
     * maxLen chars, whichever comes first, adding an ellipsis
     * if the value was actually truncated. If a newline appears before
     * any other non-whitepspace characters then ignore it.
     * 
     * @return truncated string
     */
    static String truncnl(String value, int maxLen) {
        int truncLen = maxLen
        String trimmed = value.trim()    
        if(maxLen > trimmed.size())
            truncLen = trimmed.size()
        
        int nlIndex = trimmed.indexOf('\n')
        if(nlIndex >=0) 
            return trimmed.substring(0, Math.min(truncLen, nlIndex)) + "..."
        else {
            return trimmed.substring(0, truncLen) + ((truncLen < maxLen) ? "" : "...")
        }
    }
    
    /**
     * Return the given string indented by 4 spaces.  Highly inefficient.
     */
    static String indent(String value) {
        if(value == null)
            return "    null"
        value.split("\n")*.replaceAll("\r","").collect { "    " + it }.join("\n")
    }
    
    static Pattern LEADING_WHITESPACE = ~/^[\s]*/
    
    /**
     * Join commands split over newlines together into single lines in a
     * safe way so that the user can write their commands containing newlines.
     * <p>
     * When a blank line is encountered it is preserved as  new line, and
     * the previous line is appended with a semicolon if no other terminator
     * is present.
     */
    static String joinShellLines(String cmd) {
        String joined = ""
        boolean embeddedQuoteChar = false
        cmd.trim().eachLine { String line ->
            
            if(line.indexOf('"')>=0 || line.indexOf("'")>=0)
                embeddedQuoteChar = true
            
            if(!embeddedQuoteChar) 
                line = LEADING_WHITESPACE.matcher(line).replaceAll("")
                
            if(!line.trim().isEmpty() || joined.isEmpty()) {
                joined += " " + line
            }
            else {
                if(!joined.trim().endsWith(";") && !joined.trim().endsWith("&"))
                    joined += ";"
                    
                joined += " "
            }
        }
        return joined.trim()
    }
    
    /**
     * Emulate how the shell parses command line arguments by splitting
     * on spaces while being sensitive to embedded quote characters
     * 
     * @return
     */
    static List<String> splitShellArgs(String cmd) {
        final int UNQUOTED = 0
        final int IN_SQ = 1
        final int IN_DQ = 2
        final int NOESC=0
        final int INESC=1
        
        int state = 0
        int escState = 0
        
        String lastChar = ""
        
        List<String> args = []
        
        StringBuilder arg = new StringBuilder()
        for(c in cmd) {
            
            if(escState == INESC) {
                arg.append(c)
                escState = NOESC
                continue
            }
            
            switch(c) {
                case "'":
                    if(state == 0)
                        state = 1
                    else
                    if(state == 1)
                        state = 0
                    else
                      arg.append(c)
                break
                
                case '"':
                  if(state == 0)
                      state = 2
                  else
                  if(state == 2)
                      state = 0
                  else
                      arg.append(c)
                break
                
                case ' ':
                case '\t':
                    if(state == 0) {
                        args.add(arg.toString())
                        arg = new StringBuilder()
                    }
                    else
                      arg.append(c)
                break
                
                case '\\':
                    escState = INESC
                    break
                    
                default:
                    arg.append(c)
           }
        }
        args.add(arg.toString())
        
        return args
    }
    
    /**
     * Return true if the underlying operating system is Windows
     */
    public static boolean isWindows() {
       String os = System.getProperty("os.name").toLowerCase();
       return (os.indexOf("win") >= 0);
   }
    
    /**
     * Return true if the underlying operating system is Windows
     */
   public static boolean isLinux() {
       String os = System.getProperty("os.name").toLowerCase();
       return (os.indexOf("linux") >= 0);
   }
    
   public static String sha1(String message) {
       
       MessageDigest digest = MessageDigest.getInstance("SHA1")
       
       ByteArrayInputStream bytes = new ByteArrayInputStream(message.bytes)
       DigestInputStream   dis = new DigestInputStream(bytes, digest);
    
        // read the file and update the hash calculation
        while (dis.read() != -1) {} ;
    
        // get the hash value as byte array
        byte[] hash = digest.digest();
    
        return byteArray2Hex(hash);
    }
    
    private static String byteArray2Hex(byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
    
    /**
     * Close all the streams associated with the given process
     * ignoring all exceptions.
     * <p>
     * Note: this is necessary because if the streams are not closed
     * this way it seems they can take a while to be closed, even though
     * the process may have ended. If many processes are executed consecutively
     * the file handle limit can be exhausted even though processes are not 
     * executed concurrently.
     */
    public static withStreams(Process p, Closure c) {
        try {
            return c()
        }
        finally {
          try { p.inputStream.close() } catch(Throwable t) { }      
          try { p.outputStream.close() } catch(Throwable t) { }      
          try { p.errorStream.close() } catch(Throwable t) { }      
        }
    }
    
    public static String upperCaseWords(String text) {
        return text.split().collect { it.capitalize() }.join(' ')
    }
    
    // See https://gist.github.com/310321
    // Licensed under the "Apache License, Version 2.0" (c) 2010
    /**
     * Returns filenames found by expanding the passed pattern which is String or
     * a List of patterns.
     * 
     * TODO - CLOUD - convert this to use nio Path and DirectoryStream interfaces
     * 
     * NOTE: that this pattern is not a regexp (it's closer to a shell glob).
     * NOTE: that case sensitivity depends on your system.
     *
     * <code>*</code>      Matches any file. Can be restricted by other values in
     *                     the glob pattern (same as <code>.*</code> in regexp).
     *                     <code>*</code> will match all files,
     *                     <code>c*</code> will match all files beginning with c,
     *                     <code>*c</code> will match all files ending with c.
     *
     * <code>**</code>     Matches directories recursively.
     *
     * <code>?</code>      Matches any one character. Equivalent to <code>.</code>
     *                     in a regular expression.
     *
     * <code>[set]</code>  Matches any one character in set. Behaves like character
     *                     sets in regex, including negation (<code>[^a-z]</code>).
     *
     * <code>{p,q}</code>  Matches either literal p or literal q. Matching literals
     *                     may be more than one character in length. More than two
     *                     literals may be specified. Same as alternation in regexp.
     *
     * NOTE: when matching special characters an escape is required, for example :
     * <code>"\\*"</code> or <code>"\\\\"</code>.
     *
     * NOTE: flags (e.g. case insensitive matching) are not supported.
     *
     * @see http://ruby-doc.org/core/classes/Dir.html
     * @see http://www.faqs.org/docs/abs/HTML/globbingref.html
     * @author Karol Bucek
     */
    static List<String> glob(pattern) {
        
        if(pattern instanceof Pattern)
            return regexGlob(pattern)
        
        if ( pattern == null ) throw new IllegalArgumentException('null pattern')
        if ( pattern instanceof Collection
            || pattern instanceof Object[] ) {
            if ( pattern.size() == 0 ) return []
            return pattern.toList().sum({ glob(it) })
        }
        def base = '', path = pattern.tokenize('/')
        int i = -1, s = path.size()
        while ( ++i < s - 1 ) {
            // STOP on 'wild'-cards :
            // 1. * (equivalent to /.*/x in regexp)
            // 2. ? (equivalent to /.{1}/ in regexp)
            // 3. [set]
            // 4. {p,q}
            if ( path[i] ==~ /.*[^\\]?[\*|\?|\[|\]|\{|\}].*/ ) break
        }
        base = path[0..<i].join('/'); 
        if(pattern.startsWith("/"))
            base = "/" + base
        pattern = path[i..<s].join('/') 
        
        // a char loop over the pattern - instead of a bunch of replace() calls :
        char c; boolean curling = false; // (c) Vancouver 2010 :)
        final Closure notEscaped = { j -> // todo handling 2 escapes is enought !
            if ( j == 0 || pattern.charAt(j-1) != '\\' ) return true
            return ( j > 1 && pattern.charAt(j-2) == '\\') // [j-1] was '\\'
        }
        StringBuilder pb = new StringBuilder()
        for (i=0; i<(s = pattern.length()); i++) {
            switch (c = pattern.charAt(i)) {
                case ['.', '$'] as char[] : // escape special chars
                    pb.append('\\').append(c)
                    break
                case '?' as char : // 2. ?
                    if ( notEscaped(i) ) pb.append('.')
                    else pb.append(c)
                    break
                case '*' as char : // 1. * (or **)
                    if ( notEscaped(i) ) {
                        if ( i==s-1 || pattern.charAt(i+1) != '*' ) pb.append('.*?')
                        else (pb.append('**') && i++) // skip next *
                    }
                    else pb.append(c)
                    break
                case '{' as char : // 4. {a,bc} -> (a|bc)
                    if ( notEscaped(i) ) { pb.append('('); curling = true }
                    else pb.append(c)
                    break
                case ',' as char : // 4. {a,bc} -> (a|bc)
                    if ( notEscaped(i) && curling ) pb.append('|')
                    else pb.append(c)
                    break
                case '}' as char : // 4. {a,bc} -> (a|bc)
                    if ( notEscaped(i) && curling ) { pb.append(')'); curling = false }
                    else pb.append(c)
                    break
                default : pb.append(c)
            }
        }
        // if the last char is not a wildcard match the end :
        if ( c != '?' && c != ')' && c != ']' ) pb.append('$')
        pattern = pb.toString()
        // meh - a nice one :
        // new File('').exists() != new File(new File('').absolutePath).exists()
        final File baseFile = new File(base).getAbsoluteFile() // base might be ''
        final List fnames = [] // the result - file names
        //println "base: $base pattern: $pattern"
        if ( baseFile.exists() ) { // do not throw a FileNotFoundException
            final List matchedDirs = [ baseFile ]
            if ( (path = pattern.tokenize('/')).size() > 1 ) {
                // list and yield all dirs of the given dir :
                final Closure listDirs = { dir, yield ->
                    for ( File file : dir.listFiles() )
                        if ( file.isDirectory() ) yield.call(file, yield)
                }
                path[0..-2].each { subPattern ->
                    final boolean global = (subPattern == '**')
                    // match the dir, second param is the closure itself :
                    final Closure matchDir = { dir, self ->
                        if ( global || dir.name ==~ subPattern ) {
                            matchedDirs.add(dir)
                        }
                        if ( global ) listDirs(dir, self) // recurse
                    }
                    File[] mdirs = matchedDirs.toArray(); matchedDirs.clear()
                    for ( File mdir : mdirs ) {
                        if ( global ) matchedDirs.add(mdir)
                        listDirs( mdir, matchDir )
                    }
                }
            }
            // we used the absolute path - thus might need to remove the 'prefix' :
            s = base ? baseFile.path.lastIndexOf(base) : (baseFile.path.length() + 1)
            // add the files matching in a given directory to the result :
            final Closure addMatchingFiles = { dir, p ->
                dir.list({ pdir, name ->
                    if ( name ==~ p ) fnames << "${pdir.path}/$name".substring(s)
                    return false // we do not care about the list() return value
                } as FilenameFilter)
            }
            for (i = 0; i<matchedDirs.size(); i++) {
                // we only need the match agains the last "path"
                // aka the pattern was tokenized with '/' :
                addMatchingFiles(matchedDirs[i], path[-1])
            }
        }
        return fnames.sort()
    }
    
    /**
     * TODO   CLOUD - convert this to use directorystream / Path interface
     * 
     * @param globPattern
     * @return a list of files whose name matches the given regex
     */
    static List<String> regexGlob(Pattern globPattern) {
        File f = new File(globPattern.toString())
        File dir = f.parentFile != null ? f.parentFile : new File(".")
        Pattern pattern = Pattern.compile(f.name)
        def result = dir.listFiles().grep { pattern.matcher(it.name).matches() }*.path        
        return result
    }
    
    final static int TEMP_DIR_ATTEMPTS = 500
    
    /**
     * Create a temporary directory 
     * (Based on Guava library)
     */
    public static File createTempDir() {
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        String baseName = Long.toString(System.nanoTime()) + "-";
      
        for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
          File tempDir = new File(baseDir, baseName + counter);
          if (tempDir.mkdir()) {
            return tempDir;
          }
        }
        
        throw new IllegalStateException("Failed to create directory within $TEMP_DIR_ATTEMPTS")
      }
    
    /**
     * Return true if the specified token is a part of the given file name, separated by delimiters
     * @param token
     * @param name
     * @return
     */
    static boolean isTokenInName(String token, String name) {
        
    }
    
    static List collectText(Object n, Closure c) {
        collectText(n, [], c)
    }
    
    /**
     * Collect text strings from an XML document
     * @param n
     * @param allText
     * @return
     */
    static List collectText(Object n, List results, Closure c) {
        if (n.getClass().getName() == "java.lang.String") {
            results.add(c(n))
        } else {
            n.children().each { child ->
                collectText(child, results, c)
            }
        }
        return results
    }
    
    static escape(Object value) {
        return XmlUtil.escapeXml(String.valueOf(value))
    }
    
    static String quote(String value) {
        '"' + value.replaceAll('"','\\"') + '"'        
    }    
    
    @CompileStatic
    static time(String desc, Closure c) {
        Date startTime = new Date()
        try {
            c()
        }
        finally {
            Date endTime = new Date()
            log.info "$desc executed in " + TimeCategory.minus(endTime,startTime) 
        }
    }
    
   /**
    * Coerce all of the arguments to point to files in the specified directory.
    */
    @CompileStatic
    static List toDir(List outputs, dir) {
        
       String targetDir = dir
       File targetDirFile = new File(targetDir)
       if(!targetDirFile.exists())
           targetDirFile.mkdirs()
           
       String outPrefix = targetDir == "." ? "" : targetDir + "/" 
       
       List<Class> types = outputs.collect { it.class }
       def newOutputs = outputs.collect { out ->
           Class type = out.class
           
           String outString = out.toString()
           if(outString.contains("/") && outString.contains("*")) 
               return out
           else
           if(out instanceof PipelineFile) {
               PipelineFile pf = (PipelineFile)out
               String newPath = targetDir == "." ? pf.name : targetDir + '/' + pf.name
               return pf.newName(newPath)
           }
           else
           if(outString.contains("/") && new File(outString).exists())  {
               return out
           }
           else {
             def result = outPrefix + new File(out.toString()).name 
             return type == Pattern ? Pattern.compile(result) : result
           }
       }
       
       return newOutputs
    }
    
    static String urlToFileName(String url, String defaultExt) {
        String result = url.replaceAll('^.*/','')

        if(result.isEmpty())
            result = new URI(url).getHost().replaceAll("^www\\.","") + ".${defaultExt}"
            
        if(!result.contains(".")) {
            result += "." + defaultExt
        }   
        
        return result
    }
    
    @CompileStatic
    static String ext(String fileName) {
        int index = fileName.lastIndexOf('.')
        if(index < 0 || index >= fileName.size()-1)
            return ""
        return fileName.substring(index+1)
    }
    
    /**
     * Reduce continuous runs of the same value down to a single instance.
     * 
     * @param values    List of strings to reduce
     * @return          List containing all the values in values, but with
     *                  sequential recurrent values reduced to single instance
     */
    static List removeRuns(List<String> values) {
        removeRuns(values,values)
    }
    
    static List removeRuns(List<Object> target, List<String> values) {
        def last = new Object()
        target[values.findIndexValues {
          def result = (last == null) || (last != it)
          last = it
          return result
        }]
    }
    
    /**
     * Cache canonical paths for non-canonical paths
     * 
     * This is used because querying these paths is extremely slow on some file systems and
     * especially when there is high concurrency can bottleneck all of Bpipe for minutes.
     */
    static HashMap<String,File> canonicalFiles = new ConcurrentHashMap<String, File>(1000)
    
    @CompileStatic
    static File canonicalFileFor(String path) {
        File result = canonicalFiles[path]
        if(result != null)
            return result 
            
        result = new File(path).canonicalFile
        canonicalFiles[path] = result
        
        if(canonicalFiles.size() % 1000 == 0)
            log.info "Number of cached canonical paths = " + canonicalFiles.size()
            
        return result
    }
    
    /**
     * Set up simple logging to work in a sane way
     * 
     * @param path
     * @return
     */
    public static configureSimpleLogging(String path) {
        def parentLog = Logger.getLogger("bpipe.Runner").parent
        parentLog.getHandlers().each { parentLog.removeHandler(it) }
        FileHandler fh = new FileHandler(path)
        fh.setFormatter(new BpipeLogFormatter())
        parentLog.addHandler(fh)
    }
    
   public static void configureVerboseLogging() {
        ConsoleHandler console = new ConsoleHandler()
        console.setFormatter(new BpipeLogFormatter())
        console.setLevel(Level.FINE)
        log.getParent().addHandler(console)
    }
   
   public static resolveRscriptExe() {
       resolveExe("R", "Rscript")
   }
   
   /**
    * Check if an executable has been configured by the user and if so, return the full path to it
    */
   public static String resolveExe(String name, String defaultExe) {
       String resolvedExe = defaultExe
       if(Config.userConfig.containsKey(name) && Config.userConfig[name].containsKey("executable")) {
           resolvedExe = Config.userConfig[name].executable
           File exeFile = new File(resolvedExe)
           if(!exeFile.exists()) {
               Path scriptParentDir = new File(Config.config.script).absoluteFile.parentFile.toPath()
               Path relativeToPipeline = scriptParentDir.resolve(exeFile.toPath())
               if(Files.exists(relativeToPipeline)) {
                   String pathRelativeToPipeline = relativeToPipeline.toFile().absolutePath
                   log.info "Interpreting tool path $resolvedExe as relative to pipeline: $pathRelativeToPipeline"
                   resolvedExe = pathRelativeToPipeline
               }
           }
           log.info "Using custom $name executable: $resolvedExe"
       }
       return resolvedExe
   }
   
   static Pattern GROOVY_EXT = ~'\\.groovy$'
   
   /**
    * Compute a nicely formatted stack trace, with groovy generated script names 
    * replaced with corresponding bpipe script names 
    * 
    * @param t  Exception to format
    * @return   String value of stack trace, nicely formatted
    */
    @CompileStatic
    static String prettyStackTrace(Throwable t) {
        
        Throwable sanitized = StackTraceUtils.deepSanitize(t)
        StringWriter sw = new StringWriter()
        sanitized.printStackTrace(new PrintWriter(sw))
        String stackTrace = sw.toString()
        Pipeline.scriptNames.each { String fileName, String internalName ->  
            stackTrace = stackTrace.replaceAll(internalName, fileName.replaceAll(GROOVY_EXT,'')) 
        }
        return stackTrace
    }
    
    /**
     * Execute the given command and return back a map with the exit code,
     * the standard output, and std err 
     * <p>
     * An optional closure will be executed as a delegate of the ProcessBuilder created
     * to allow configuration.
     * 
     * @param startCmd  List of objects (will be converted to strings) as args to command
     * @return Map with exitValue, err and out keys
     */
    @CompileStatic
    static ExecutedProcess executeCommand(Map options = [:], List<Object> startCmd, @DelegatesTo(ProcessBuilder) Closure builder = null) {
        
        List<String> stringified = startCmd*.toString()
        
        log.info "Executing command: " + stringified.join(' ')
        
        ProcessBuilder pb = new ProcessBuilder(stringified)
        if(builder != null) {
            builder.delegate = pb
            builder()
        }
        
        Process p = pb.start()
        ExecutedProcess result = new ExecutedProcess()
        Utils.withStreams(p) {
            Appendable out = (Appendable)options.out ?: new StringBuilder()
            Appendable err = (Appendable)options.err ?: new StringBuilder()
            
            // Note: observed issue with hang here on Broad cluster
            // seems to be related to hang inside OS / NFS call. Maybe use forwarder for this?
            p.waitForProcessOutput(out, err)
            
            result.exitValue = p.waitFor()
            result.err = err
            result.out = out
        }        
        
        if(options.throwOnError && result.exitValue != 0) 
            throw new Exception("Command returned exit code ${result.exitValue}: " + stringified.join(" ") + "\n\nOutput: $result.out\n\nStd Err:\n\n$result.err")
            
        return result
    }
    
    static boolean isProcessRunning(String pid) {
        String info = "ps -o ppid,ruser -p ${pid}".execute().text
        def lines = info.split("\n")*.trim()
        if(lines.size()>1)  {
            info = lines[1].split(" ")[1]; 
            if(info == System.properties["user.name"]) {
                return true
            }
        }        
        return false
    }
    
    static String formatErrors(Collection<PipelineError> errors) {
        
        int width = Config.config.columns
        
        return errors.collect {  PipelineError e ->
            String branch = e.ctx?.branch?.name
            if(branch != null && branch != "all") {
                branch = " ( $branch ) "
            }
            
            (e.ctx ? " $e.ctx.stageName $branch " : "").center(width,"-") + "\n\n" + e.message + "\n"
        }.join("\n") + "\n" + ("-" * width)
        
        
    }
    
    
    final static long SECOND=1000L
    final static long MINUTE= 60 * SECOND
    final static long HOUR=60 * MINUTE
    final static long DAY=24*HOUR
    
    /**
     * Convert a time specified in a configuration to ms
     * 
     * @return
     */
    static long walltimeToMs(Object timeSpec) {
        
        String stringValue = String.valueOf(timeSpec)
        
        // If integer, then assume it is seconds
        if(stringValue.isInteger()) {
            return ((long)stringValue.toInteger()) * 1000L
        }
        
        // If not integer, parse in format DD:HH:MM
        List<String> parts = stringValue.tokenize(":")*.toInteger().reverse()
        return [[SECOND, MINUTE, HOUR, DAY], parts].transpose().sum { unitAndValue ->
            unitAndValue[0]*unitAndValue[1]
        }
    }
    
    static Pattern TRIM_SECONDS = ~',[^,]*?seconds$'
    
    static Pattern TRIM_ZEROS = ~'\\.000 seconds$'
    
    static void table(Map options = [:], List<String> headers, List<List> rows) {
        
        String indent = options.indent ? (" " * options.indent) : ""
        
        def out = options.out ?: System.out
        
        // Create formatters
        Map formatters = options.get('format',[:])
        headers.each { h ->
            if(!formatters[h])
                formatters[h] = { String.valueOf(it) }
            else 
            if(formatters[h] instanceof Closure) {
                // just let it be - it will be called and expected to return the value
            }
            else { // Assume it is a String.format specifier (sprintf style)
                String spec = formatters[h]
                if(spec == "timespan") {
                    formatters[h] = { times ->
                        TimeCategory.minus(times[1],times[0]).toString().replaceAll(TRIM_SECONDS, '').replaceAll(TRIM_ZEROS,' seconds')
                    }
                }
                else {
                    formatters[h] = { val ->
                        String.format(spec, val)
                    }
                }
            }
        }
        
        // Create renderers
        Map renderers = options.get('render',[:])
        headers.each { hd ->
            if(!renderers[hd]) {
                renderers[hd]  = { val, width  -> out.print val.padRight(width) }
            }
        }
        
        // Find the width of each column
        Map<String,Integer> columnWidths = [:]
        if(rows) {
            headers.eachWithIndex { hd, i ->
                Object widestRow = rows.max { row -> formatters[hd](row[i]).size() }
                columnWidths[hd] = Math.max(hd.size(), formatters[hd](widestRow[i]).size())
            }
        }
        else {
            headers.each { columnWidths[it] = it.size() }
        }
            
        // Now render the table
        String header = headers.collect { hd -> hd.center(columnWidths[hd]) }.join(" | ")
        
        if(options.topborder) {
            out.println indent + ("-" * header.size())
        }
        
        out.println indent + header
        out.println indent + ("-" * header.size())
        
        rows.each { row ->
            int i=0
            headers.each { hd -> 
                if(i!=0)
                    out.print(" | ");
                else
                    out.print(indent)
                    
                 renderers[hd](formatters[hd](row[i++]), columnWidths[hd])
            }
            out.println ""
        }
    }
    
    /**
     * Wait until action returns a non-null result, with a timeout
     * returns an object with ok and timeout methods that accept closure
     * arguments for actions to take.
     * 
     * @param timeoutMs
     * @param action
     * @return
     */
    static Map waitWithTimeout(long timeoutMs, Closure action) {
        return [
            ok: { okAction ->
                long startMs = System.currentTimeMillis()
                while(true) {
                    def result = action()
                    if(result != null && result != false)
                        return [ timeout: { return okAction(result) }]
                    Thread.sleep(100)
                    
                    if(System.currentTimeMillis() - startMs > timeoutMs)
                        return [ timeout: {  it() }]
                }
            },
            
            timeout: { timeoutAction ->
                long startMs = System.currentTimeMillis()
                while(true) {
                    def result = action()
                    if(result != null && result != false)
                        return 
                    Thread.sleep(100)
                    
                    if(System.currentTimeMillis() - startMs > timeoutMs) {
                        timeoutAction()
                        return
                    }
                }
            } 
        ]
    }
    
    static Map sanitizeForSerialization(Object obj) {
       obj.clone()
          .collect {(it.value instanceof PipelineStage) ? ["stage",it.value.toProperties()] : it}
          .collectEntries() 
    }
    
    @CompileStatic
    static String sendURL(Map<String,Object> params, String method, String baseURL, Map headers=[:], Map postParams=null) {
		String paramString = params?.collect {
			URLEncoder.encode(it.key)+'='+URLEncoder.encode(String.valueOf(it.value))
		}?.join('&')
		
		String url
        if(!params) {
            url = baseURL
        }
        else
		if(baseURL.contains("?"))
			url = baseURL + '&' + paramString
		else
			url = baseURL + '?' + paramString

        String postParamString = null
        if(postParams) {
            postParamString = postParams.collect {
                URLEncoder.encode(String.valueOf(it.key))+'='+URLEncoder.encode(String.valueOf(it.value))
            }.join('&')
        }			

        connectAndSend(method, postParamString, url, headers)
	}
	
    @CompileStatic
    static String connectAndSend(def contentIn, String url, Map headers=[:]) {
		connectAndSend('POST', contentIn, url, headers)
	}
	
    static String connectAndSend(String method, def contentIn, String url, Map headers=[:]) {
        
        new URL(url).openConnection().with {
            if(method == 'POST')
                doOutput = true
            useCaches = false
			headers.each { h ->
	            setRequestProperty(h.key,h.value)
            }
			
            requestMethod = method
                
            connect()
                
			if(contentIn != null) {
	            outputStream.withWriter { writer ->
	              writer << contentIn
	            }
			}
			else {
                if(method == 'POST')
    				outputStream.close()
			}
            log.info "Sent to URL $url"
                
            int code = getResponseCode()
            log.info("Received response code $code from server")
            if(code >= 400) {
                String output = errorStream.text
                throw new PipelineError("Send to $url failed with error $code. Response contains: ${output.take(80)}")
            }
                    
            if(log.isLoggable(Level.FINE))
                log.fine content.text
				
			return inputStream.text
        }
    }
    
    static closeQuietly(obj) {
        if(obj == null)
            return
        try {
            obj.close()
        }
        catch(Exception e) {
            // ignore   
        }
    }
    

    @CompileStatic
    static Object withRetries(Map options=[:], int maxRetries, Closure action) {
       int count = 0
       long sleepTimeMs = 1000
       long backoffBaseTime = 10000
       if(options.backoffBaseTime)
           backoffBaseTime = (long)options.backoffBaseTime
           
        while(true) {
            try {
                return action()
            }
            catch(Exception e) {
                if(count > maxRetries)
                    throw e
            }
            if(options.message != null)
                log.info "Try $count of $maxRetries ($options.message) ..."
            else
                log.info "Try $count of $maxRetries ..."
            Thread.sleep(sleepTimeMs)
            ++count
            sleepTimeMs = Math.max(backoffBaseTime,sleepTimeMs*2)
        }
    } 
    
    static logException(Logger logger, String msg, Throwable t) {
       logger.log(
           Level.SEVERE,
           msg + "Exception: $t.message", 
           t)
    }
    
    /**
     * Convert the given value to a string and then do some operations to normalise
     * the path into a nicer form (eg: remove preceding ./, etc).
     * 
     * @param cleanPath
     * @return  String value representing a good path to expose to commands or the user
     */
    @CompileStatic
    static String cleanPath(Object value) {
       return String.valueOf(value).replaceAll('^./','') 
    }
    
    @CompileStatic
    public static void touchPaths(List<Path> outOfDateOutputs) {
        final FileTime now = FileTime.from(Instant.now())
        outOfDateOutputs.each { Path p ->
            if(Files.exists(p))
                Files.setLastModifiedTime(p, now)
            println("MSG: Touching file: $p")
        }
    }
    
    @CompileStatic
    public static Map configToMap(Map m) {
        if(m instanceof ConfigObject) {
            m = m.collectEntries { it }
        }
        
        for(Map.Entry e in m) {
            if(e.value instanceof Map) {
                e.value = configToMap((Map)e.value)
            }
        }
        return m
    }
}
