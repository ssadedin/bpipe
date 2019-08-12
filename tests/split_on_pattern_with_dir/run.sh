source ../testsupport.sh

run cases/*.gz  controls/*.gz

grep -q 'The inputs are cases/BP1-10_R1.fastq.gz cases/BP1-10_R2.fastq.gz$' test.out || err "Failed to see correct inputs printed"

