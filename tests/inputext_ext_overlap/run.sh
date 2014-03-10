source ../testsupport.sh

bpipe run test.groovy test.xcnv > test.out 2>&1

notexists test.hello.csv

true

