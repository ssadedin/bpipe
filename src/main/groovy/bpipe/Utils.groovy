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
import java.lang.management.OperatingSystemMXBean;
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Miscellaneous internal utilities used by Bpipe
 * 
 * @author ssadedin@mcri.edu.au
 */
@Log
class Utils {
    
    /**
     * Returns a list of output files that appear to be out of date
     * because they are either missing or older than one of the input
     * files
     * 
     * @param outputs   a single string or collection of strings
     * @param inputs    a single string or collection of strings
     * @return
     */
    static List findOlder(def outputs, def inputs) {
        
        // Some pipeline stages don't expect any outputs
        // Fixing issue 44 - https://code.google.com/p/bpipe/issues/detail?id=44
        if( !outputs || !inputs )
            return []
            
        // Remove any directories appearing as inputs - their timestamps change whenever
        // any file in the dir changes
        inputs = inputs.grep { (it != null) && !(new File(it).isDirectory())}

        // Box into a collection for simplicity
        outputs = box(outputs)
        
        def inputFiles = inputs.collect { new File(it) }
    
        outputs.collect { new File(it) }.grep { outFile ->
            
            log.info "===== Check $outFile ====="
            if(!outFile.exists()) {
                log.info "file doesn't exist: $outFile"
                return true
            }
                
            if(inputs instanceof String || inputs instanceof GString) {
                if(outFile.name == inputs) {
                    return false
                }
                else {
                    log.info "Check $inputs : " + new File(inputs).lastModified() + " <=  " + outFile + " : " + outFile.lastModified() 
                    return (new File(inputs).lastModified() > outFile.lastModified()) 
                }
            }
            else
            if(isContainer(inputs)) {
                log.info "Checking $outFile against inputs $inputs"
                return inputFiles.any { inFile ->
                    log.info "Check $inFile : " + inFile.lastModified() + " >  " + "$outFile : " + outFile.lastModified() 
                    inFile.lastModified() > outFile.lastModified() 
                }
            }
            else 
                throw new PipelineError("Don't know how to interpret $inputs of type " + inputs.class.name)
                
            return true    
        }
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
            
            if(!f.exists()) {
                log.info "File $f does not appear to exist: listing directory to flush file system"
                f.parentFile.listFiles()
                if(f.exists())
                    log.info("File $f revealed by listing directory")
            }
            
            if(f.exists()) {  
                // it.delete() 
                File trashDir = new File(".bpipe/trash")
                if(!trashDir.exists())
                    trashDir.mkdirs()
                    
                File dest = new File(trashDir, f.name)
                if(!Runner.opts.t) {
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
                else
                    println "[TEST MODE] Would clean up file $f to $dest" 
            }
            else {
                log.info "Not cleaning up file $f because it does not exist"
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
    static Collection box(outputs) {
        
        if(outputs == null)
            return []
        
        if(outputs instanceof Collection)
            return outputs
        
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
     * Check if any of the specified inputs are wrapped in PipelineInput and if so, unwrap them
     * 
     * @param inputs    a single object or array or collection of objects
     */
    static unwrap(inputs) {
        def result = unbox(box(inputs).collect { it instanceof PipelineInput?it.input:it }.flatten())
        return result
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
    * Coerce all of the arguments (which may be an array of Strings or a single String) to
    * point to files in the specified directory.
    */
    static toDir(def outputs, dir) {
       
       String targetDir = dir
       File targetDirFile = new File(targetDir)
       if(!targetDirFile.exists())
           targetDirFile.mkdirs()
           
       String outPrefix = targetDir == "." ? "" : targetDir + "/" 
       def boxed = Utils.box(outputs)
       def types = boxed.collect { it.class }
       def newOutputs = boxed.collect { 
           Class type = it.class
           if(it.toString().contains("/") && it.toString().contains("*")) 
               return it
           else
           if(it.toString().contains("/") && new File(it).exists()) 
               return it
           else {
             def result = outPrefix + new File(it.toString()).name 
             return type == Pattern ? Pattern.compile(result) : result
           }
       }
       return Utils.unbox(newOutputs)
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
        def last = new Object()
        values.grep {
          def result = (last == null) || (last != it)
          last = it
          return result
        }
    }
}