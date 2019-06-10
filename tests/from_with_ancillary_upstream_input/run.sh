source ../testsupport.sh

run test.foo.txt test.bar.txt

grep -q 'test.bar.there.bam correct=test.bar.there.bam' test.out || err "Incorrect input used: does not correspond to from"

true
