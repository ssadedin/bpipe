source ../testsupport.sh

run test.groovy test.csv test1.fastq.gz test2.fastq.gz 

grep -q 'Stage hello' test.out || err "Failed to find stage hello in output"

exists test.xml test1_fastqc.zip test2_fastqc.zip test.tsv
