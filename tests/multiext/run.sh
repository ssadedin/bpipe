source ../testsupport.sh

run

grep -q 'foo.vcf.gz.*foo.vcf.world.txt' test.out || err "Selected wrong output from produce when only partial suffix matches"

exists foo.vcf.world.txt


