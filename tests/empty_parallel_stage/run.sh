source ../testsupport.sh

run test.vcf

grep -q '.*test.there.vcf.* ->.*test.there.world.vcf.*' test.out || err 'Incorrect file copied after empty parallel stage'
