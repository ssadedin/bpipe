source ../testsupport.sh

run 

grep -q "this is the first command using 2 threads" test.out || err "Failed to find first command using 2 threads"

grep -q "another command using 5 threads" test.out || err "Failed to find second command using 5 threads"


true

