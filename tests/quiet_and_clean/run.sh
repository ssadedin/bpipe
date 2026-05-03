source ../testsupport.sh

bpipe run test.groovy > test.out 2>&1 &

# Wait for first bpipe to actually be running (run.pid is written after lock is acquired)
for i in $(seq 1 30); do
    if [ -f .bpipe/run.pid ]; then
        break
    fi
    sleep 1
done

export BPIPE_QUIET=true

bpipe run test.groovy > test.out2 2>&1 &

# Wait for second bpipe to produce output (JVM startup can be slow on CI)
for i in $(seq 1 15); do
    if [ -s test.out2 ]; then
        break
    fi
    sleep 1
done

grep -q "Quiet mode enabled: auto-aborting this pipeline" test.out2 || err "Failed to find auto abort message when quiet mode enabled - output was: $(cat test.out2)"

kill %1 %2

true
