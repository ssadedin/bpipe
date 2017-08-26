######################################
#
# Bpipe Pooled Command Script
# Pool: $cfg.name
#
######################################

export POOL_ID="$cfg.name"

${bpipeUtilsShellCode}

mkdir -p "`dirname $stopFile`"
mkdir -p .bpipe/commandtmp/$cmd.id

(
i=0
    while true;
    do
        while [ ! -e ${pooledCommandScript}.[0-9]*.sh ];
        do
    
            if [ -e "$stopFile" ];
            then
                $debugLog && { echo "Pool command exit flag detected for pool ${cmd.id}: $stopFile" >> pool.log; }
                exit 0
            fi
    
            sleep 1
    
            $debugLog && { echo "No: ${pooledCommandScript}.[0-9]*.sh" >> pool.log; }
    
            let 'i=i+1'
            $debugLog && { echo "i=\$i" >> pool.log; }
            if ! $persistent;
            then
                if [ "\$i" == ${HEARTBEAT_INTERVAL_SECONDS+5} ];
                then
                    if [ ! -e "$heartBeatFile" ];
                    then
                        $debugLog && { echo "Heartbeat not found: exiting" >> pool.log; }
                        exit 0
                    fi
                    $debugLog && { echo "Remove heartbeat: $heartBeatFile" >> pool.log; }
                    i=0
                    rm $heartBeatFile
                else
                    $debugLog && { echo "In between heartbeat checks: \$i" >> pool.log; }
                fi
            fi
        done
    
        POOL_COMMAND_SCRIPT=`ls ${pooledCommandScript}.[0-9]*.sh` 
        POOL_COMMAND_SCRIPT_BASE=\${POOL_COMMAND_SCRIPT%%.sh}
        POOL_COMMAND_ID=\${POOL_COMMAND_SCRIPT_BASE##*.}
    
        $debugLog && { echo "Pool $cmd.id Executing command: \$POOL_COMMAND_ID" >> pool.log; }
    
        mv \$POOL_COMMAND_SCRIPT $pooledCommandScript
    
        POOL_COMMAND_EXIT_FILE=.bpipe/commandtmp/$cmd.id/\${POOL_COMMAND_ID}.pool.exit
        POOL_COMMAND_STOP_FILE=.bpipe/commandtmp/$cmd.id/\${POOL_COMMAND_ID}.pool.stop
    
        ( 
         set +e
         bash -e $pooledCommandScript >> .bpipe/commandtmp/$cmd.id/pool.out 2>>.bpipe/commandtmp/$cmd.id/pool.err;
         echo \$? > \${POOL_COMMAND_EXIT_FILE}.tmp
         mv \${POOL_COMMAND_EXIT_FILE}.tmp \${POOL_COMMAND_EXIT_FILE}
        ) &
        
        JOB_PID=\$!
        
        $debugLog && { echo "Pool $cmd.id has child command pid: \$JOB_PID" >> pool.log; }
        
        while [ ! -f \$POOL_COMMAND_EXIT_FILE ];
        do
            sleep 1;
            if [ -e \$POOL_COMMAND_STOP_FILE ];
            then
                $debugLog && { echo "Pool $cmd.id detected stop file \$POOL_COMMAND_STOP_FILE for command \$POOL_COMMAND_ID, kill \$JOB_PID" >> pool.log; }
                killtree \$JOB_PID
                echo "-1" > \$POOL_COMMAND_EXIT_FILE
            fi
        done
        
        $debugLog && { echo "Pool $cmd.id finished command: \$POOL_COMMAND_ID" >> pool.log; }
    done
)

$debugLog && { echo "Removing pool file $poolFile" >> pool.log; }

rm -f $poolFile
