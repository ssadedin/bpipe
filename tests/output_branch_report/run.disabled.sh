source ../testsupport.sh

run test.txt

exists test.cat.bar.hello.csv test.cat.foo.hello.csv test.frog.bar.hello.csv test.frog.foo.hello.csv

exists test.cat.bar.hello.world.xml test.frog.bar.hello.world.xml

exists output.html

grep -q test.frog.bar.hello.world.xml output.html || err "Failed to find expected XML output file in report"
grep -q test.cat.bar.hello.world.xml output.html || err "Failed to find expected XML output file in report"

grep -q test.frog.foo.hello.world.xml output.html && err "Found unexpected XML output file in report (from wrong branch)"


true
