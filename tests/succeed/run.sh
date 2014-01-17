source ../testsupport.sh

run test.txt

exists test.hello.csv
exists test.hello.world.xml

grep -q "Stage mars" test.out && err "Stage executed incorrectly"

grep -qi "failed" test.out && err "Something failed but there should have been no failure"

true

