package bpipe

import static org.junit.Assert.*

import org.junit.After
import org.junit.Before
import org.junit.Test

class SQLiteOutputMetaDataStoreTest {
    
    SQLiteOutputMetaDataStore store
    
    @Before
    void setUp() {
        // Minimal config needed for store initialization
        Config.userConfig = new ConfigObject()
        store = new SQLiteOutputMetaDataStore()
    }
    
    @After
    void tearDown() {
        if(store != null) {
            store.close()
        }
        // Clean up the test database
        new File(".bpipe/outputs/outputs.db")?.delete()
    }
    
    @Test
    void testSaveAndLoadAll() {
        
        OutputMetaData p = new OutputMetaData()
        p.outputPath = "test_output.txt"
        p.canonicalPath = "/tmp/test_output.txt"
        p.stageName = "test_stage"
        p.commandId = "cmd-1"
        p.command = "echo hello > test_output.txt"
        p.branchPath = ""
        p.inputs = ["input.txt"]
        p.timestamp = System.currentTimeMillis()
        p.startTimeMs = p.timestamp - 1000
        p.createTimeMs = p.timestamp - 2000
        p.stopTimeMs = p.timestamp
        p.preserve = false
        p.intermediate = false
        p.cleaned = false
        p.stub = false
        p.tools = "echo:1.0"
        p.fingerprint = "abc123"
        p.storage = bpipe.storage.StorageLayer.create('local')
        p.basePath = "/tmp"
        p.outputFile = new PipelineFile(p.outputPath, p.storage)
        
        store.save(p)
        store.flush()
        
        List<OutputMetaData> all = store.loadAll()
        assertNotNull all
        assertEquals 1, all.size()
        
        OutputMetaData loaded = all[0]
        assertEquals "test_output.txt", loaded.outputPath
        assertEquals "test_stage", loaded.stageName
        assertEquals "cmd-1", loaded.commandId
        assertEquals ["input.txt"], loaded.inputs
        assertEquals false, loaded.cleaned
        assertEquals false, loaded.preserve
    }
    
    @Test
    void testExists() {
        
        OutputMetaData p = new OutputMetaData()
        p.outputPath = "exists_test.txt"
        p.canonicalPath = "/tmp/exists_test.txt"
        p.stageName = "test"
        p.timestamp = 1000L
        p.storage = bpipe.storage.StorageLayer.create('local')
        p.outputFile = new PipelineFile(p.outputPath, p.storage)
        
        assertFalse store.exists(p)
        
        store.save(p)
        store.flush()
        
        assertTrue store.exists(p)
    }
    
    @Test
    void testUpdate() {
        
        OutputMetaData p = new OutputMetaData()
        p.outputPath = "update_test.txt"
        p.canonicalPath = "/tmp/update_test.txt"
        p.stageName = "update_stage"
        p.timestamp = 1000L
        p.cleaned = false
        p.preserve = false
        p.storage = bpipe.storage.StorageLayer.create('local')
        p.outputFile = new PipelineFile(p.outputPath, p.storage)
        
        store.save(p)
        store.flush()
        
        p.preserve = true
        p.cleaned = true
        store.update(p)
        store.flush()
        
        List<OutputMetaData> all = store.loadAll()
        OutputMetaData loaded = all.find { it.outputPath == "update_test.txt" }
        assertNotNull loaded
        assertEquals "update_test.txt", loaded.outputPath
        assertEquals true, loaded.preserve
        assertEquals true, loaded.cleaned
    }
    
    @Test
    void testSaveAll() {
        
        List<OutputMetaData> items = []
        for(int i = 0; i < 5; i++) {
            OutputMetaData p = new OutputMetaData()
            p.outputPath = "batch_${i}.txt"
            p.canonicalPath = "/tmp/batch_${i}.txt"
            p.stageName = "batch"
            p.timestamp = 1000L + i
            p.storage = bpipe.storage.StorageLayer.create('local')
            p.outputFile = new PipelineFile(p.outputPath, p.storage)
            items.add(p)
        }
        
        store.saveAll(items)
        store.flush()
        
        List<OutputMetaData> all = store.loadAll()
        assertEquals 5, all.size()
    }
}