source ../testsupport.sh

run test.xml

grep -q "ERROR: Expected output file" test.out && err "Pipeline output not corretly recognised after reference in check"

grep -q "Pipeline Failed" test.out && err "Pipeline failed unexpectedly"

true


