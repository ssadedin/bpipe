#!/bin/sh

#echo ">>>>>>>>>>>>>>>> ENTERING COMMAND TEMPLATE <<<<<<<<<<<<<<<<<<<<<<"

<%if(config?.use) {%>
use ${config.use}
<%}%>

<%if(config?.modules) {%>
module load ${config.modules}
<%}%>

echo \$\$ > ${CMD_PID_FILE}

CMD_SHELL=bash
(command -v bash  > /dev/null) || CMD_SHELL=sh

cat ${CMD_FILENAME} | $SETSID \$CMD_SHELL -e 

result=\$?
echo -n \$result > $jobDir/${CMD_EXIT_FILENAME}.tmp
mv $jobDir/${CMD_EXIT_FILENAME}.tmp $jobDir/${CMD_EXIT_FILENAME}

<%if(config?.post_cmd) {%>
${config.post_cmd}
<%}%>

#echo ">>>>>>>>>>>>>>>> EXITING COMMAND TEMPLATE <<<<<<<<<<<<<<<<<<<<<<"

exit \$result