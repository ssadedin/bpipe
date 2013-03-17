source ../testsupport.sh

run test.txt test2.txt

# the world stage should create an output file from the CSV files created in hello stage
exists test.hello.world.txt

# Check that it really used BOTH CSV files created by the earlier nested stage
grep -q 'test.hello.csv test2.hello.csv' commandlog.txt || err "Failed to use ex[ected csv files for final output"

true
