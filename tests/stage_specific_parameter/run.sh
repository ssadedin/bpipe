source ../testsupport.sh

run

grep -q "Foo = gotcha" test.out || err "Did not observe expected default env value of foo=gotcha"

grep -q "Default Foo = ooogiee" test.out || err "Did not observe expected default value of foo=ooogiee"

bpipe run --env bingo test.groovy > test.out 

grep -q "Default Foo = ooogiee" test.out || err "Did not observe expected default value of foo=ooogiee"

grep -q "Foo = yayyy" test.out || err "Did not observe expected environment specific value of foo=yayyy"
