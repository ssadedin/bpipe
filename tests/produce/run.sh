source ../testsupport.sh

# Remove files produced by previous run (if any)
rm -f test.out_1 test.out_2

# Run the test
run test.txt

# Check for valid result
grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.out_1 ] && err "Failed to find expected output test.out_1"
[ ! -f test.out_2 ] && err "Failed to find expected output test.out_2"

true
