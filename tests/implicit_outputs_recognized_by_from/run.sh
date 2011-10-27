
# Git seems to get the timestamps wrong?! We need this file to be most recent
touch *.vcf

runtest s_1.txt.sorted.dedupe.reorder.recal.realign.rg.bam

grep -q "apply_calibration" test.out || err "Failed to find expected stage"

grep -q "Would execute" test.out || err "Failed to find would execute:  test should be able to execute"
