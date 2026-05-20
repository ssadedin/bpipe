source ../testsupport.sh

# Test that stub mode creates placeholder output files and propagates downstream

# First, run in dev mode with stub response
rm -f *.txt *.csv
rm -rf .bpipe

# Run bpipe in background with dev mode enabled at hello stage
bpipe dev test.groovy > test.out 2>&1 &
BPIPE_PID=$!

# Wait for the dev prompt to appear
sleep 5

# Send stub command
echo "stub" > .bpipe/dev_continue

# Wait for pipeline to complete
wait $BPIPE_PID 2>/dev/null

# Check that the stub output was created
if [ ! -f "test.hello.txt" ]; then
    echo "ERROR: Stub output file test.hello.txt was not created"
    exit 1
fi

# Check that the file is empty (stub)
if [ -s "test.hello.txt" ]; then
    echo "ERROR: Stub output file test.hello.txt should be empty but has content"
    exit 1
fi

# Check that the downstream output was also created (propagated stub)
if [ ! -f "test.hello.world.csv" ]; then
    echo "ERROR: Downstream stub output test.hello.world.csv was not created"
    exit 1
fi

# Check that the metadata marks it as a stub
PROP_FILE=$(ls .bpipe/outputs/hello.test.hello.txt.properties 2>/dev/null)
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
