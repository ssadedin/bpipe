
source ../testsupport.sh

run start.bam

grep -q 'start.bam -> start.there.bam' test.out || err "Did not use correct input for there stage"

true
