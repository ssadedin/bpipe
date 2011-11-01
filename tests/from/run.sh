source ../testsupport.sh

rm -f test.txt.hello test.csv.world *.hello *.world

run test.txt test.csv

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"

[ ! -f test.txt.hello ] && err "Failed to find expected output test.txt.hello"
[ ! -f test.csv.world ] && err "Failed to find expected output test.csv.world"

true
