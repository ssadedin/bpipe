source ../testsupport.sh

rm check.txt

run 

grep -q "Stage check" test.out || err "Failed to find expected stage check"

[ ! -f check.txt ] && err "Failed to find expected output check.txt"

true
