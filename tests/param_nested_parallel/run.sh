source ../testsupport.sh

run s_1.txt

grep -q "foo = cat" test.out || err "Failed to find expected output foo = cat"
grep -q "foo = bar" test.out || err "Failed to find expected output foo = bar"

true
