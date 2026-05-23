// This script defines a segment (which internally calls Pipeline.execute and sets
// rootPipeline), then throws an IllegalArgumentException in the preamble before
// the Bpipe.run {} closure is ever invoked. This simulates the real-world scenario
// where segment evaluation causes rootPipeline to be non-null and not-failed,
// leading to an incorrect "ok" status in the agent reply hook.

hello = {
    exec "echo hello"
}

my_segment = Bpipe.segment {
    hello
}

// Now simulate a failure during script initialization after segment definition
def samples = ["SAMPLE_A", "SAMPLE_A"]  // duplicate

def seen = [] as Set
for(s in samples) {
    if(s in seen)
        throw new IllegalArgumentException("Sample $s appears more than once. This is not supported.")
    seen << s
}

Bpipe.run {
    my_segment
}
