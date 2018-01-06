source ../testsupport.sh

bpipe test -v test.groovy test*.csv test*.xml > test.out

grep -q "walltime:00:02:00" test.out || err "Incorrect walltime used"
