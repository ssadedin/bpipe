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

import groovy.util.logging.Log;

import java.security.DigestInputStream
import java.security.MessageDigest

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

        // Box into a collection for simplicity
        outputs = box(outputs)
        
        def inputFiles = inputs.collect { new File(it) }
    
        outputs.collect { new File(it) }.grep { outFile ->
            
//            println "===== Check $outFile ====="
            if(!outFile.exists()) {
//                println "file doesn't exist: $outFile"
                return true
            }
                
            if(inputs instanceof String || inputs instanceof GString) {
                if(outFile.name == inputs) {
                    return false
                }
                else {
//                    println "Check $inputs : " + new File(inputs).lastModified() + " <=  " + outFile + " : " + outFile.lastModified() 
                    return (new File(inputs).lastModified() > outFile.lastModified()) 
                }
            }
            else
            if(isContainer(inputs)) {
//                println "Checking $outFile against inputs $inputs"
                return inputFiles.any { inFile ->
//                    println "Check $inFile : " + inFile.lastModified() + " >  " + "$outFile : " + outFile.lastModified() 
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
     */
    static void cleanup(def outputs) {
        if(!outputs)
            return
            
        if(!(outputs instanceof Collection))
            outputs = [outputs]
        
        outputs.collect { new File(it) }.each { File f -> if(f.exists()) {  
            // it.delete() 
            File trashDir = new File(".bpipe/trash")
            if(!trashDir.exists())
                trashDir.mkdirs()
                
            File dest = new File(trashDir, f.name)
                
            if(!Runner.opts.t) {
                println "Cleaning up file $f to $dest" 
                f.renameTo(dest)
            }
            else
                println "[TEST MODE] Would clean up file $f to $dest" 
         }}
    }
    
    /**
     * Return true if the specified object is a collection or
     * array, false otherwise.
     */
    static boolean isContainer(def obj) {
        return (obj != null) && (obj instanceof Collection || obj.class.isArray())
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
        def result = unbox(box(inputs).collect { it instanceof PipelineInput?it.input:it }).flatten()
        return result
    }
    
    
    /**
     * Truncate the input at the first new line or at most 
     * maxLen chars, whichever comes first, adding an ellipsis
     * if the value was actually truncated.
     * 
     * @return truncated string
     */
    static String truncnl(String value, int maxLen) {
        int truncLen = maxLen
        if(maxLen > value.size())
            truncLen = value.size()
            
        int nlIndex = value.indexOf('\n')
        if(nlIndex >=0) 
            return value.substring(0, Math.min(truncLen, nlIndex)) + "..."
        else {
            return value.substring(0, truncLen) + ((truncLen < maxLen) ? "" : "...")
        }
    }
    
    /**
     * Return the given string indented by 4 spaces.  Highly inefficient.
     */
    static String indent(String value) {
        value.split("\n")*.replaceAll("\r","").collect { "    " + it }.join("\n")
    }
    
    /**
     * Return true if the underlying operating system is Windows
     */
    public static boolean isWindows() {
       String os = System.getProperty("os.name").toLowerCase();
       return (os.indexOf("win") >= 0);
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
        base = path[0..<i].join('/'); pattern = path[i..<s].join('/')
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
        return fnames
    }
}