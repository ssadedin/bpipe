#!/bin/bash

source ../testsupport.sh

# Placeholder: AppCDS tests are not run automatically due to
# Java version requirements (17+) and potential flakiness.
# To test manually:
#   bpipe appcds                              # generate base archive
#   bpipe appcds train pipeline.groovy input   # generate dynamic archive
#   bpipe appcds clear [pipeline.groovy]       # clear archives

echo "AppCDS test skipped (manual testing only)" > test.out
true
