
source ../testsupport.sh

run sample1.txt sample2.txt


grep -q 'Processing world for sample1 using sample1.hello.txt' test.out || err "Used unexpected input file for downstream channel based stage: should have used sample1.hello.txt"

