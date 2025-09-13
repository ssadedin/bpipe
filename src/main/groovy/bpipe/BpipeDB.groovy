package bpipe

import groovy.transform.CompileStatic
import groovy.util.logging.Log

@CompileStatic
@Log
class BpipeDB {
    
    private static String dbPath = System.getenv("BPIPE_DB_DIR") ?: 
                                 System.getProperty("user.home") + "/.bpipedb"
    
    static String getDbPath() {
        log.info "Using DB path: $dbPath"
        return dbPath
    }
    
    static File getFile(String path) {
        File dbDir = new File(getDbPath())
        if(!dbDir.exists()) {
            if(!dbDir.mkdirs()) {
                throw new IOException("Unable to create bpipe database directory: $dbDir")
            }
        }
        return new File(dbDir, path)
    }
}
