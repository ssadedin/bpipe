package bpipe

import groovy.transform.CompileStatic

@CompileStatic
class BpipeDB {
    
    private static String dbPath = null
    
    static String getDbPath() {
        if(dbPath != null) {
            log.info "Using cached DB path: $dbPath"
            return dbPath
        }
            
        // Check config first
        def cfg = Config.userConfig as ConfigObject
        log.info "Checking config for DB path. Config: $cfg"
        if(cfg?.containsKey('db') && ((ConfigObject)cfg.db)?.containsKey('directory')) {
            dbPath = ((ConfigObject)cfg.db).directory.toString()
            log.info "Using configured DB path from config: $dbPath"
        }
        else {
            dbPath = System.getProperty("user.home") + "/.bpipedb"
            log.info "Using default DB path: $dbPath"
        }
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
