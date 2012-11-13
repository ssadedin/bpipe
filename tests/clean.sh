#!/bin/bash

# Clean up standard unwanted files
for i in .bpipe test.out commandlog.txt doc ;
do
	find . -name "$i" | xargs rm -rf
done

# Run individual cleanup scripts
for i in `find . -name cleanup.sh`; do cd `dirname $i`; chmod uga+rx ./cleanup.sh; ./cleanup.sh; cd - > /dev/null 2>&1 ; done

