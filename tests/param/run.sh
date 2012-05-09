source ../testsupport.sh

run

grep -q "foo = cat" test.out || err "Failed to find expected output foo = cat"
grep -q "foo = tree" test.out || err "Failed to find expected output foo = tree"
grep -q "foo = fubar" test.out || err "Failed to find expected output foo = fubar"

true
