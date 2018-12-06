source ../testsupport.sh

run samples.txt

grep -q "I am processing files for SAMPLE1.*MALE" test.out  || err "Did not see correct output for SAMPLE1"
grep -q "I am processing files for SAMPLE2.*FEMALE" test.out  || err "Did not see correct output for SAMPLE2"

exists test1_R1.fastq.SAMPLE1.real_stage.txt
exists test2_R1.fastq.SAMPLE2.real_stage.txt
