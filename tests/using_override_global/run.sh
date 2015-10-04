source ../testsupport.sh

run 

grep -q "Foo = frog" test.out || err "Did not observe local stage variable overriding global value, expect Foo = frog"

true
