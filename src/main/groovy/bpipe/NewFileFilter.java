/*
 * Copyright (c) 2016 MCRI, authors
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
package bpipe;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class NewFileFilter implements DirectoryStream.Filter<Path>{
    
    Map<String,Long> timestamps;
    
    static Logger log = Logger.getLogger("NewFileFilter");
    
    public NewFileFilter(Map<String, Long> timestamps) {
        super();
        this.timestamps = timestamps;
    }

    @Override
    public boolean accept(Path entry) throws IOException {
        
        String fileName = entry.getFileName().toString();
        
        if(timestamps != null) {
            Long timestamp = timestamps.get(fileName);
            if(timestamp == null)
                return true; // must be a new file
            
            if(timestamp >= Files.getLastModifiedTime(entry).toMillis())
                return false;
        }
        
        return isNonExcludedOutput(fileName, entry);
    }
    
    static boolean isNonExcludedOutput(String fileName, Path path) {
        return !(fileName.equals("commandlog.txt")
                  || fileName.endsWith(".log") || Files.isDirectory(path));
    }
    
    /**
     * Scan the given directory and return only files that have newer timestamps than those given
     * 
     * @param dir
     * @param timestamps
     * @return
     * @throws IOException
     */
    static List<Path> scanOutputDirectory(String dir, Map<String,Long> timestamps) throws IOException {
        File dirFile = new File(dir);
        if(!dirFile.exists())
            return new ArrayList<Path>();
                    
        try(DirectoryStream<Path> ds = Files.newDirectoryStream(dirFile.toPath(), new NewFileFilter(timestamps))) {
            List<Path> results = new ArrayList<Path>();
            for(Path p : ds) {
               results.add(p);
            }
            return results;
        }
    }
  
}
