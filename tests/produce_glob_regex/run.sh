source ../testsupport.sh

run

grep -q "Output is \(./\)\{0,1\}hello4_world.xml" test.out  || err "Failed to find correct output matched by regex glob"

true
