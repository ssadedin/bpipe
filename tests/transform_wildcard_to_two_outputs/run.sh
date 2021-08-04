source ../testsupport.sh

run  test1.g.vcf.gz test2.g.vcf.gz

exists test1.trio.gvcf.gz test1.vcf.gz
