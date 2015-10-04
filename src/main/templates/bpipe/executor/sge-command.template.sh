#!/bin/sh
#\$ -wd ${bpipe.Runner.canonicalRunDirectory}
#\$ -N "$name"
#\$ -terse
#\$ -o $jobDir/$CMD_OUT_FILENAME
#\$ -e $jobDir/$CMD_ERR_FILENAME
#\$ -notify
${additional_options}

trap 'echo 255 > $jobDir/$CMD_EXIT_FILENAME;' HUP INT TERM QUIT KILL STOP USR1 USR2

(
bash -e ${CMD_FILENAME.absolutePath}
)

result=\$?
echo -n \$result > $jobDir/$CMD_EXIT_FILENAME
exit \$result
