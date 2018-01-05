source ../testsupport.sh

run test.txt test1.xls  test2.csv   test2.xls

grep -q "Stage world" test.out || err "Failed to execute stage world"

exists test.txt.hello test1.world.xml test*.world.tsv

true

