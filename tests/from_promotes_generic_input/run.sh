source ../testsupport.sh

run test.txt

grep -q 'test.txt -> something.txt' test.out || err 'File specified by from not used as default input'

true
