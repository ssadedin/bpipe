source ../testsupport.sh

run test1.gz test2.gz

exists test1.sai test2.sai test1.bam

grep -q '^execute' test.out || err "Incorrectly skipped some commands on first (clean) run"

run test1.gz test2.gz

grep -q '^execute' test.out && err "Failed to skip all the steps"

rm *.bam

run test1.gz test2.gz

grep -q '^execute' test.out || err "Incorrectly skipped whole transform when some outputs missing"
grep -q '^execute multi' test.out && err "Failed to skip first commands in transform even though outputs up to date"

# Remove just 1 of the targets that the multi block creates
rm *.bam test1.sai

run test1.gz test2.gz

# get created, but this is a limitation of Bpipe 
grep -q '^execute multi2' test.out && err "Failed to skip creation of output test2.gz that was already up to date"
grep -q '^execute multi1' test.out || err "Incorrectly skipped creation of test1.gz even though it was removed"

true
