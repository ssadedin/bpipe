source ../testsupport.sh

rm -f test.world.txt test.txt

run *.fastq

grep -q "SureSelect_Capture_11MG2107_AD0AN0ACXX_GATCAG_L002_R1.fastq SureSelect_Capture_11MG2107_AD0AN0ACXX_GATCAG_L002_R2.fastq" test.out || err "Failed to find expected GATCAG files R1 and R2"
grep -q "SureSelect_Capture_11MG2108_AD0AN0ACXX_TAGCTT_L002_R1.fastq SureSelect_Capture_11MG2108_AD0AN0ACXX_TAGCTT_L002_R2.fastq" test.out || err "Failed to find expected TAGCTT files R1 and R2"

true
