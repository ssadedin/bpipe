source ../testsupport.sh

# Clean up from any previous runs
rm -rf  test.world.txt customdb jobs.txt .bpipe 

# Set custom DB location
export BPIPE_DB_DIR="$PWD/customdb"

run

# Check pipeline executed successfully
grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

# Verify outputs were created
exists test.txt test.world.txt

# Verify custom db location was used and populated with actual job files
[ "$(ls -A customdb/completed)" ] || err "No job files found in customdb/completed directory"

# Wait a moment for job to complete and be registered
sleep 2

../../bin/bpipe jobs > jobs.txt

grep -q "No active / recent jobs" jobs.txt && { echo "Error: no jobs found when running 'bpipe jobs'"; echo; echo "Output:"; echo; cat jobs.txt; exit 1; }

grep -q "custom_db_location" jobs.txt || { echo "Error: this pipeline not found in the jobs that were run using 'bpipe jobs'"; echo "Output: "; echo; cat jobs.txt; exit 1; }

true
