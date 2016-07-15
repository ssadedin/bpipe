#!/bin/bash

source ../testsupport.sh

run test.groovy

[ `grep -c 'Executing for chr1' test.out` == 2 ] || err "Wrong number of branches for chr1 (expected 2)"

[ `grep -c 'Executing for chr2' test.out` == 1 ] || err "Wrong number of branches for chr2 (expected 1)"

true
