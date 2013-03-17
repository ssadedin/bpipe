source ../testsupport.sh

run test.txt

grep -q "Stage world" test.out || err "Failed to execute stage world"

exists test.txt.hello test1.world.xml test*.world.tsv

true

