#!/bin/bash
source ../testsupport.sh

BPIPE_LIB=./modules bpipe run test.groovy > test.out

grep -q "Error evaluating script" test.out && err "Error while evaluating script"

grep -q "hello world" test.out || err "Failed to find expected expression 'hello world' in output"

true
