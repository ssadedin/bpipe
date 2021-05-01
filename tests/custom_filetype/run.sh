source ../testsupport.sh

run test.vcf.gz test2.vcf.gz

exists test.hello.csv test.take.my.vcf test.take.xml test.take.vcf test2.world.csv

notexists test.world.vcf

bash cleanup.sh

run test.vcf test2.vcf

exists test.hello.csv test.take.my.vcf test.take.xml test.take.vcf test2.world.csv

