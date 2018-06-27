source ../testsupport.sh

run start.bam

grep -q 'Input to there.*start.hello.earth.bam' test.out || err "Incorrect input provided to stage following parallel section"

true
