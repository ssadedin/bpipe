source ../testsupport.sh

rm -f test.world.txt test.txt

run

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"
grep -q "1 1   cow" test.out || err "Failed to find expeced output '1 1   cow'"

true
