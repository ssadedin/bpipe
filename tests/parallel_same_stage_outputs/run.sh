source ../testsupport.sh

source ./cleanup.sh

# note option -n 1 is important for this test
bpipe run -n 1 test.groovy s_1.txt > test.out 2>&1
 
grep -q "Stage how_are_you" test.out || err "Failed to find expected stage how_are_you"
grep -q "Stage take_me_to_your_leader" test.out || err "Failed to find expected stage take_me_to_your_leader"
grep -q "Stage end" test.out || err "Failed to find expected stage end"

grep -q "inputs=.*s_1.txt.how_are_you.a .*s_1.txt.take_me_to_your_leader.a" test.out || err "Failed to find expected stage end"


true
