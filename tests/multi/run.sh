source ../testsupport.sh

run 

exists foo.txt bar.txt

grep -q "Stage async_stuff" test.out || err "Failed to find expected stage async_stuff in output"
grep -q "Command.*failed" test.out || err "Failed to find expected failed command in output"
grep -q "Exit status = 1" test.out || err "Failed to find expected exit status in output"


true
