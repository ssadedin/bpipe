source ../testsupport.sh

run 

grep -q "World : Cheese" test.out || err "Branch variable set by child in parent not printed out in parent"

true
