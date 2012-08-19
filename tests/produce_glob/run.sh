source ../testsupport.sh

rm -f *.csv *.xml

run test.txt

grep -q "Stage hello" test.out || err "Failed to find expected stage hello"
grep -q "Stage world" test.out || err "Failed to find expected stage world"
grep -q "Inputs are ./test.csv ./test[0-9].xml ./test[0-9].xml ./test[0-9].xml" test.out || err "Failed to find expected output (test1.xml,test2.xml,test3.xml)"

[ ! -f test1.xml ] && err "Failed to find expected output test1.xml"
[ ! -f test2.xml ] && err "Failed to find expected output test2.xml"
[ ! -f test3.xml ] && err "Failed to find expected output test3.xml"

run test.txt

grep -q "Skipping steps to create" test.out || err "Failed to find expected output 'Skipping steps ...'"

true
