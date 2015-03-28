source ../testsupport.sh

run

grep -q "This is the correct message" test.out || err "Did not find message from world stage loaded in branch"
grep -q "this should execute" test.out || err "Did not find message from globally defined world stage"
grep -q "should not execute" test.out && err "Message that should not have been printed was observed"

true



