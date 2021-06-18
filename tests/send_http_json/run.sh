source ../testsupport.sh

run

grep -q 'Pipeline Succeeded' test.out || err "Failed to observe Pipeline Succeeded message"

# run again to ensure that retrying the same pipeline does not redo calls to http
bpipe run -p RETRY=true test.groovy

true
