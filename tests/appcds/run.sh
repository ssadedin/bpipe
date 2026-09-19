#!/bin/bash

source ../testsupport.sh

# Test that the appcds command works on Java 17+
# On older Java, the generate step will fail but that is expected

JAVA_MAJOR=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)(\.[0-9]+)*.*"/\1/')

if [ "$JAVA_MAJOR" -ge 17 ] 2>/dev/null; then

    # Clean any prior archive
    run_bpipe appcds clear > test.out 2>&1

    # Generate the archive
    run_bpipe appcds > test.out 2>&1

    # Check that the archive was created
    if [ -f "$BPIPE_HOME/lib/appcds/bpipe.jsa" ] || [ -f "$HOME/.bpipe/appcds/bpipe.jsa" ]; then
        echo "AppCDS archive created successfully" >> test.out
    else
        echo "ERROR: AppCDS archive was not created" >> test.out
        exit 1
    fi

    # Run a simple pipeline to verify it works with the archive
    run_bpipe run test.groovy > test.out 2>&1

    grep -q "Successfully completed" test.out || err "Pipeline did not complete successfully with AppCDS"

    # Clean up
    run_bpipe appcds clear > /dev/null 2>&1

    true

else
    echo "Java version $JAVA_MAJOR < 17, skipping AppCDS test" > test.out
    true
fi
