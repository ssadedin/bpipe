source ../testsupport.sh

export BPIPE_LIB=./bpipelib

rm -f test.*.txt

run

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"
grep -q "Stage jupiter" test.out || err "Failed to find expected stage jupiter"
grep -q "Stage saturn" test.out || err "Failed to find expected stage saturn"
grep -q "Stage mars" test.out || err "Failed to find expected stage mars"
grep -q "Stage external" test.out || err "Failed to find expected stage external"

[ ! -f test.txt ] && err "Failed to find expected output test.txt"
[ ! -f test.world.txt ] && err "Failed to find expected output test.world.txt"
[ ! -f test.jupiter.txt ] && err "Failed to find expected output test.jupiter.txt"
[ ! -f test.saturn.txt ] && err "Failed to find expected output test.saturn.txt"
[ ! -f test.mars.txt ] && err "Failed to find expected output test.mars.txt"

true
