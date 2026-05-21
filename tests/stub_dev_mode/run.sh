source ../testsupport.sh

# Test that stub mode creates placeholder output files and propagates downstream

# First, run in dev mode with stub response
rm -f *.txt *.csv
rm -rf .bpipe

# Run bpipe in background with dev mode enabled at hello stage
bpipe dev test.groovy > test.out 2>&1 &
BPIPE_PID=$!

# Wait for the "Waiting for changes" message to appear in output (signals dev mode is waiting)
WAIT_COUNT=0
while ! grep -q "Waiting for changes" test.out 2>/dev/null && [ $WAIT_COUNT -lt 30 ]; do
    sleep 1
    WAIT_COUNT=$((WAIT_COUNT + 1))
done

if ! grep -q "Waiting for changes" test.out 2>/dev/null; then
    echo "ERROR: dev mode did not reach waiting state"
    cat test.out
    exit 1
fi

# Give it a moment to be ready to read the file
sleep 2

# Send stub command for first stage
echo "stub" > .bpipe/dev_continue

# Wait for the second "Waiting for changes" message (second stage)
WAIT_COUNT=0
while [ $WAIT_COUNT -lt 30 ]; do
    sleep 1
    WAIT_COUNT=$((WAIT_COUNT + 1))
    # Check if pipeline already finished
    if ! kill -0 $BPIPE_PID 2>/dev/null; then
        break
    fi
    # Check if second "Waiting for changes" appeared
    WAIT_MSG_COUNT=$(grep -c "Waiting for changes" test.out 2>/dev/null || echo 0)
    if [ "$WAIT_MSG_COUNT" -ge 2 ]; then
        sleep 1
        break
    fi
done

# Send empty response for second stage (auto-stub will kick in since inputs are stubs)
if kill -0 $BPIPE_PID 2>/dev/null; then
    echo "" > .bpipe/dev_continue
fi

# Wait for pipeline to complete
WAIT_COUNT=0
while kill -0 $BPIPE_PID 2>/dev/null && [ $WAIT_COUNT -lt 30 ]; do
    sleep 1
    WAIT_COUNT=$((WAIT_COUNT + 1))
done

wait $BPIPE_PID 2>/dev/null

# Check that the stub output was created
if [ ! -f "hello.txt" ]; then
    echo "ERROR: Stub output file hello.txt was not created"
    cat test.out
    exit 1
fi

# Check that the file is empty (stub)
if [ -s "hello.txt" ]; then
    echo "ERROR: Stub output file hello.txt should be empty but has content"
    exit 1
fi

# Check that the downstream output was also created (propagated stub)
if [ ! -f "hello.world.csv" ]; then
    echo "ERROR: Downstream stub output hello.world.csv was not created"
    exit 1
fi

# Check that the metadata marks it as a stub
PROP_FILE=$(ls .bpipe/outputs/hello.hello.txt.properties 2>/dev/null)
if [ -z "$PROP_FILE" ]; then
    echo "ERROR: No properties file found for stub output"
    exit 1
fi

if ! grep -q "stub=true" "$PROP_FILE"; then
    echo "ERROR: Properties file does not contain stub=true"
    exit 1
fi

echo "TEST PASSED: Stub mode works correctly"
exit 0
