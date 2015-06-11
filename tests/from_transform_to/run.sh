source ../testsupport.sh

bpipe run -p AMPLICON_BED=amplicons.bed test.groovy test.bed test.xml > test.out

[ -e amplicons.fasta ] || err "Incorrect output file: file name 'amplicon.fasta' should have been inferred from transform"

true
