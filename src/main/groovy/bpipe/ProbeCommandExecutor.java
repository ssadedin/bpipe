package bpipe;

import java.util.List;
import java.util.Map;

public class ProbeCommandExecutor implements CommandExecutor {

    private static final long serialVersionUID = 1L;

    @Override
    public void start(Map cfg, String id, String name, String cmd) {

    }

    @Override
    public String status() {
        return null;
    }

    @Override
    public int waitFor() {
        return 0;
    }

    @Override
    public void stop() {
    }
    
    public void cleanup() {
    }

    @Override
    public List<String> getIgnorableOutputs() {
        return null;
    }

}
