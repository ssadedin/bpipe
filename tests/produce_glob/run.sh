source ../testsupport.sh

rm -f *.csv *.xml

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"
grep -q "Inputs are ./test.csv ./test1.xml ./test2.xml ./test3.xml" test.out || err "Failed to find expected output (test1.xml,test2.xml,test3.xml)"

[ ! -f test1.xml ] && err "Failed to find expected output test1.xml"

run test.txt

grep -q "Skipping steps to create" test.out || err "Failed to find expected output 'Skipping steps ...'"

true
