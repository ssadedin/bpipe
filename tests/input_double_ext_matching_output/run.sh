source ../testsupport.sh

run test.foo.csv

grep -q "Using bad.csv" test.out && err "Incorrect input referenced: input does not have all extensions specified by multi pipeline input with double extension"

exists bad.csv hello.xml

true
