source ../testsupport.sh

run

grep -q "Hi there"  test.out || err "Did not see expected message Hi there in output"


true
