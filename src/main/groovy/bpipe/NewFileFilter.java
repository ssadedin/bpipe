package bpipe;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NewFileFilter implements DirectoryStream.Filter<Path>{
    
    Map<String,Long> timestamps;
    
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
