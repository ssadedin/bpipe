package bpipe.executor

import static org.junit.Assert.*

import org.junit.Before
import org.junit.Test

import bpipe.CommandStatus

class TorqueStatusMonitorTest {
    
    @Before
    void before() {
        bpipe.Config.userConfig = new ConfigObject()
        bpipe.Utils.configureVerboseLogging()
//        bpipe.Utils.configureSimpleLogging()
    }

    @Test
    public void test() {
        
        String output = """
            <Data><Job><Job_Id>1000</Job_Id><Job_Name>snpid_analysis</Job_Name><Job_Owner>joe.blogs@server</Job_Owner><resources_used><cput>00:00:05</cput><energy_used>0</energy_used><mem>544560kb</mem><vmem>36939848kb</vmem><walltime>00:00:07</walltime></resources_used><job_state>C</job_state><queue>shortrun</queue><server>mgt</server><Account_Name>vcgs-vip</Account_Name><Checkpoint>u</Checkpoint><ctime>1577049180</ctime><Error_Path>dev1.meerkat.mcri.edu.au:/misc/vcgs/seq/cpipe-2.3-staging/batches/verify_23019_0001_cpipe/analysis/snpid_analysis.e3740317</Error_Path><exec_host>comp014/21</exec_host><Hold_Types>n</Hold_Types><Join_Path>n</Join_Path><Keep_Files>n</Keep_Files><Mail_Points>a</Mail_Points><mtime>1577049199</mtime><Output_Path>dev1.meerkat.mcri.edu.au:/misc/vcgs/seq/cpipe-2.3-staging/batches/verify_23019_0001_cpipe/analysis/snpid_analysis.o3740317</Output_Path><Priority>0</Priority><qtime>1577049180</qtime><Rerunable>True</Rerunable><Resource_List><mem>2gb</mem><nodect>1</nodect><nodes>1:ppn=1</nodes><pmem>3800mb</pmem><walltime>01:00:00</walltime></Resource_List><session_id>413481</session_id><Variable_List>PBS_O_QUEUE=shortrun,PBS_O_HOME=/home/simon.sadedin,PBS_O_LOGNAME=simon.sadedin,PBS_O_PATH=/misc/vcgs/seq/cpipe-2.3-staging:/misc/vcgs/seq/cpipe-2.3-staging/pipeline/scripts:/home/simon.sadedin/google-cloud-sdk/bin:/home/simon.sadedin/.sdkman/candidates/maven/current/bin:/home/simon.sadedin/.sdkman/candidates/java/current/bin:/home/simon.sadedin/.sdkman/candidates/groovy/current/bin:/home/simon.sadedin/.sdkman/candidates/gradle/current/bin:/home/simon.sadedin/.sdkman/candidates/crash/current/bin:/usr/local/installed/java/1.8.0_66/bin:/home/simon.sadedin/work/tools/groovy-ngs-utils/bin:/home/simon.sadedin/work/bpipe/bpipe-0.9.9.8/bin:/home/simon.sadedin/work/tools/sqlite:/group/bioi1/simons/tools/ggip/bin:/usr/local/bin:/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/sbin:/home/simon.sadedin/.local/bin:/home/simon.sadedin/bin,PBS_O_MAIL=/var/spool/mail/simon.sadedin,PBS_O_SHELL=/bin/bash,PBS_O_LANG=en_AU.UTF-8,PBS_O_WORKDIR=/misc/vcgs/seq/cpipe-2.3-staging/batches/verify_23019_0001_cpipe/analysis,PBS_O_HOST=dev1.meerkat.mcri.edu.au,PBS_O_SERVER=mgt</Variable_List><euser>simon.sadedin</euser><egroup>simon.sadedin.dg</egroup><queue_type>E</queue_type><etime>1577049180</etime><exit_status>1</exit_status><submit_args>arg1</submit_args><start_time>1577049191</start_time><start_count>1</start_count><fault_tolerant>False</fault_tolerant><comp_time>1577049199</comp_time><job_radix>0</job_radix><total_runtime>7.813160</total_runtime><submit_host>server@server.com</submit_host><request_version>1</request_version></Job></Data>
        """.stripIndent().trim()
        
        TorqueStatusMonitor tsm = new TorqueStatusMonitor()
        
        def state = new TorqueJobState(jobId:"1000", state:CommandStatus.RUNNING)
        
        tsm.jobs['1000'] = state

        tsm.updateStatus(output)
        
        assert state.exitCode == 1
        assert state.state == CommandStatus.COMPLETE
    }

    @Test
    public void 'parse multiline response'() {
        
        List output = """
            <Data><Job><Job_Id>1000</Job_Id><Job_Name>snpid_analysis</Job_Name><Job_Owner>joe.blogs@server</Job_Owner><resources_used><cput>00:00:05</cput><energy_used>0</energy_used><mem>544560kb</mem><vmem>36939848kb</vmem><walltime>00:00:07</walltime></resources_used><job_state>C</job_state><queue>shortrun</queue><server>mgt</server><Account_Name>vcgs-vip</Account_Name><Checkpoint>u</Checkpoint><ctime>1577049180</ctime><Error_Path>dev1.meerkat.mcri.edu.au:\n/misc/vcgs/seq/cpipe-2.3-staging/batches/verify_23019_0001_cpipe/analysis/snpid_analysis.e3740317</Error_Path><exec_host>comp014/21</exec_host><Hold_Types>n</Hold_Types><Join_Path>n</Join_Path><Keep_Files>n</Keep_Files><Mail_Points>a</Mail_Points><mtime>1577049199</mtime><Output_Path>dev1.meerkat.mcri.edu.au:/misc/vcgs/seq/cpipe-2.3-staging/batches/verify_23019_0001_cpipe/analysis/snpid_analysis.o3740317</Output_Path><Priority>0</Priority><qtime>1577049180</qtime><Rerunable>True</Rerunable><Resource_List><mem>2gb</mem><nodect>1</nodect><nodes>1:ppn=1</nodes><pmem>3800mb</pmem><walltime>01:00:00</walltime></Resource_List><session_id>413481</session_id><Variable_List>PBS_O_QUEUE=shortrun,PBS_O_HOME=/home/simon.sadedin,PBS_O_LOGNAME=simon.sadedin,PBS_O_PATH=/misc/vcgs/seq/cpipe-2.3-staging:/misc/vcgs/seq/cpipe-2.3-staging/pipeline/scripts:/home/simon.sadedin/google-cloud-sdk/bin:/home/simon.sadedin/.sdkman/candidates/maven/current/bin:/home/simon.sadedin/.sdkman/candidates/java/current/bin:/home/simon.sadedin/.sdkman/candidates/groovy/current/bin:/home/simon.sadedin/.sdkman/candidates/gradle/current/bin:/home/simon.sadedin/.sdkman/candidates/crash/current/bin:/usr/local/installed/java/1.8.0_66/bin:/home/simon.sadedin/work/tools/groovy-ngs-utils/bin:/home/simon.sadedin/work/bpipe/bpipe-0.9.9.8/bin:/home/simon.sadedin/work/tools/sqlite:/group/bioi1/simons/tools/ggip/bin:/usr/local/bin:/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/sbin:/home/simon.sadedin/.local/bin:/home/simon.sadedin/bin,PBS_O_MAIL=/var/spool/mail/simon.sadedin,PBS_O_SHELL=/bin/bash,PBS_O_LANG=en_AU.UTF-8,PBS_O_WORKDIR=/misc/vcgs/seq/cpipe-2.3-staging/batches/verify_23019_0001_cpipe/analysis,PBS_O_HOST=dev1.meerkat.mcri.edu.au,PBS_O_SERVER=mgt</Variable_List><euser>simon.sadedin</euser><egroup>simon.sadedin.dg</egroup><queue_type>E</queue_type><etime>1577049180</etime><exit_status>1</exit_status><submit_args>arg1</submit_args><start_time>1577049191</start_time><start_count>1</start_count><fault_tolerant>False</fault_tolerant><comp_time>1577049199</comp_time><job_radix>0</job_radix><total_runtime>7.813160</total_runtime><submit_host>server@server.com</submit_host><request_version>1</request_version></Job></Data>
        """.stripIndent()
           .trim()
           .tokenize("\n")
        
        TorqueStatusMonitor tsm = new TorqueStatusMonitor()
        
        def state = new TorqueJobState(jobId:"1000", state:CommandStatus.RUNNING)
        
        tsm.jobs['1000'] = state

        tsm.updateStatuses(output,[])
        
        assert state.exitCode == 1
        assert state.state == CommandStatus.COMPLETE
    }
}
