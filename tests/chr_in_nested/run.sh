#!/bin/bash

source ../testsupport.sh

run test.baz

grep -q 'doing chr1 foo from test.baz' test.out || err "Failed to find chr in output"
