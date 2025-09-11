source ../testsupport.sh

# Clean up from any previous runs
rm -rf test.txt test.world.txt customdb

run

# Check pipeline executed successfully
grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

# Verify outputs were created
exists test.txt test.world.txt

# Verify custom db location was used and populated with actual job files
[ "$(ls -A customdb/jobs)" ] || err "No job files found in customdb/jobs directory"
[ "$(ls -A customdb/logs)" ] || err "No log files found in customdb/logs directory"

true
