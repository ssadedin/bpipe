source ../testsupport.sh

bpipe run -d outdir test.groovy test.txt > test.out 2>&1

exists outdir/hello.output.world.xml outdir/hello.output.csv

true
