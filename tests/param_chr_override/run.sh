source ../testsupport.sh

bpipe run -L chr2 test.groovy test.txt > test.out

exists test.chr2.hello.csv

notexists test.chr1.hello.csv

true
