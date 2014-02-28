source ../testsupport.sh

bpipe run -d outdir test.groovy test.txt

exists outdir/hello.output.world.xml outdir/hello.output.csv

true
