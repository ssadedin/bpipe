# 
# This test checks that if an error occurs, the correct file and line number are reported
# it loads from two external files becuase there was a regression where different external
# files would get mixed up
#
source ../testsupport.sh

bpipe run test.groovy > test.out

grep -q 'at world.groovy.*:5' test.out || err "Failed to find correct file or line mentioned for error"

true
