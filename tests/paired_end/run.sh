source ../testsupport.sh

run test1.fastq test2.fastq ref.fa

exists test1.paired.bam

grep -q "cat test1.fastq test2.fastq ref.fa" commandlog.txt  || err "Failed to find correct command in commandlog.txt"

true
