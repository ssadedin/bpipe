source ../testsupport.sh

rm -f test.*.csv test.*.txt  test.txt.*;

run test.txt


[ ! -f test.chr1.csv ] && err "Failed to find expected output test.chr1.csv"
[ ! -f test.chr20.csv ] && err "Failed to find expected output test.chr20.csv"
[ ! -f test.chrX.csv ] && err "Failed to find expected output test.chrX.csv"

[ ! -f test.chr1.txt ] && err "Failed to find expected output test.chr1.txt"
[ ! -f test.chr20.txt ] && err "Failed to find expected output test.chr20.txt"
[ ! -f test.chrX.txt ] && err "Failed to find expected output test.chrX.txt"


grep -q 'hello there chr12' test.chr12.csv || err "Failed to find chr12 reference in test.chr12.csv"

true
