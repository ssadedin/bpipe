source ../testsupport.sh

run foo_1.fastq.gz  foo_2.fastq.gz

exists foo_1.trim.fastq.gz || err "expected transformed output foo_1.trim.fastq.gz not created"

exists foo_2.trim.fastq.gz || err "expected transformed output foo_2.trim.fastq.gz not created"

true

