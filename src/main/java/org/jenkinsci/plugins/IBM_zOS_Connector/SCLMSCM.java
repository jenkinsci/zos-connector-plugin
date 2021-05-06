package org.jenkinsci.plugins.IBM_zOS_Connector;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Joiner;
import hudson.*;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.scm.*;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * <h2>SCLMSCM</h2>
 * Class implementing SCM functionality for SCLM.s
 *
 * @author <a href="mailto:candiduslynx@gmail.com">Alexander Shcherbakov</a>
 * @version 1.0
 */
public class SCLMSCM extends SCM {
    /**
     * LPAR name or IP address.
     */
    private String server = "server";
    /**
     * FTP port to connect to.
     */
    private int port;

    /**
     * Credentials id to be converted to login+pw.
     */
    private String credentialsId;
    /**
     * FTP server JESINTERFACELEVEL=1?
     */
    private boolean JESINTERFACELEVEL1;
    /**
     * FTP transfer mode
     */
    private boolean FTPActiveMode;

    // SCLM project information (project, alternate, group, types to monitor)
    /**
     * SCLM Project Name.
     */
    private String project;
    /**
     * SCLM Alternate Project Definition.
     */
    private String alternate;
    /**
     * SCLM Group.
     */
    private String group;
    /**
     * SCLM file types under interest.
     */
    private LinkedList<String> types;
    /**
     * Step for invoking FLMCMD.
     */
    private final String JobStep;
    /**
     * Job header.
     */
    private final String JobHeader;
    /**
     * Use custom job header.
     */
    private final boolean custJobHeader;
    /**
     * Use custom FLMCMD job step.
     */
    private final boolean custJobStep;
    /**
     * Current revision state.
     */
    private SCLMSCMRevisionState currentRevision;
    /**
     * Simple logger.
     */
    private static final Logger logger = Logger.getLogger(SCLMSCM.class.getName());
    private static final String logPrefix = "SCLM: ";

    private static final String entryTemplateString = String.join(System.lineSeparator(),
            Collections.unmodifiableList(Arrays.asList(
                    "\t<entry>",
                    "\t\t<date>%s</date>",
                    "\t\t<project>%s</project>",
                    "\t\t<alternate>%s</alternate>",
                    "\t\t<group>%s</group>",
                    "\t\t<type>%s</type>",
                    "\t\t<name>%s</name>",
                    "\t\t<version>%d</version>",
                    "\t\t<userID>%s</userID>",
                    "\t\t<changeGroup>%s</changeGroup>",
                    "\t\t<editType>%s</editType>",
                    "\t</entry>"
            )));


    /**
     * Constructor that is invoked from project configuration page.
     *
     * @param server             LPAR name of IP address.
     * @param port               FTP port to connect to.
     * @param credentialsId      Credentials id..
     * @param JESINTERFACELEVEL1 JESINTERFACELEVEL=1?
     * @param FTPActiveMode      FTP data transfer mode (true=active, false=passive)
     * @param project            SCLM Project Name.
     * @param alternate          SCLM Alternate Project Definition.
     * @param group              SCLM Group.
     * @param types              Types under interest (separated by comma).
     * @param custJobStep        Whether user defines own FLMCMD job step.
     * @param JobStep            User-supplies FLMCMD job step.
     * @param custJobHeader      Whether user supplied own job header.
     * @param JobHeader          User-supplied job header.
     */
    @DataBoundConstructor
    public SCLMSCM(String server,
                   int port,
                   String credentialsId,
                   boolean JESINTERFACELEVEL1,
                   boolean FTPActiveMode,
                   String project,
                   String alternate,
                   String group,
                   String types,
                   boolean custJobStep,
                   String JobStep,
                   boolean custJobHeader,
                   String JobHeader) {
        this.server = server.replaceAll("\\s", "");
        this.port = port;
        this.credentialsId = credentialsId;
        this.JESINTERFACELEVEL1 = JESINTERFACELEVEL1;
        this.FTPActiveMode = FTPActiveMode;

        this.project = project.replaceAll("\\s", "");
        this.alternate = alternate.replaceAll("\\s", "");
        this.group = group.replaceAll("\\s", "");

        this.types = new LinkedList<>();
        for (String temp : types.split(",")) {
            temp = temp.replaceAll("\\s", "");
            if (!temp.isEmpty())
                this.types.add(temp);
        }
        this.custJobStep = custJobStep;
        if (this.custJobStep) {
            this.JobStep = JobStep;
        } else {
            this.JobStep = SCLMSCMDescriptor.SCLMJobStep;
        }
        this.custJobHeader = custJobHeader;
        if (this.custJobHeader) {
            this.JobHeader = JobHeader;
        } else {
            this.JobHeader = SCLMSCMDescriptor.SCLMJobHeader;
        }
    }

    /**
     * Dummy constructor
     */
    public SCLMSCM() {
        this.custJobHeader = false;
        this.custJobStep = false;
        this.JobStep = SCLMSCMDescriptor.SCLMJobStep;
        this.JobHeader = SCLMSCMDescriptor.SCLMJobHeader;
    }

    /**
     * Get custJobHeader.
     *
     * @return <b><code>custJobHeader</code></b>
     */
    public boolean getCustJobHeader() {
        return this.custJobHeader;
    }

    /**
     * Get custJobStep.
     *
     * @return <b><code>custJobStep</code></b>
     */
    public boolean getCustJobStep() {
        return this.custJobStep;
    }

    /**
     * Get LPAR name or IP address.
     *
     * @return <b><code>server</code></b>
     */
    public String getServer() {
        return this.server;
    }

    /**
     * Get FTP port to connect to.
     *
     * @return <b><code>port</code></b>
     */
    public int getPort() {
        return this.port;
    }

    /**
     * @return credentials id provided.
     */
    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * Get JESINTERFACELEVEL1.
     *
     * @return <b><code>JESINTERFACELEVEL1</code></b>
     */
    public boolean getJESINTERFACELEVEL1() {
        return this.JESINTERFACELEVEL1;
    }
    
    /**
     * Get FTPActiveMode
     *
     * @return <b><code>FTPActiveMode</code></b>
     */
    public boolean getFTPActiveMode() {
        return this.FTPActiveMode;
    }

    /**
     * Get SCLM Project Name.
     *
     * @return <b><code>project</code></b>
     */
    public String getProject() {
        return this.project;
    }

    /**
     * Get SCM Alternate Project Definition.
     *
     * @return <b><code>alternate</code></b>
     */
    public String getAlternate() {
        return this.alternate;
    }

    /**
     * Get SCLM Group.
     *
     * @return <b><code>group</code></b>
     */
    public String getGroup() {
        return this.group;
    }

    /**
     * Get Job header.
     *
     * @return <b><code>JobHeader</code></b>
     */
    public String getJobHeader() {
        return this.JobHeader;
    }

    /**
     * Get Job step.
     *
     * @return <b><code>JobStep</code></b>
     */
    public String getJobStep() {
        return this.JobStep;
    }

    /**
     * Get SCLM file types under interest.
     *
     * @return <b><code>types</code></b>
     */
    public String getTypes() {
        return Joiner.on(",").join(this.types);
    }

    /**
     * Fetch new remote revision.
     *
     * @param baseline Last revision.
     * @param user     username for logon
     * @param password password for logon
     * @return New remote revision.
     * @see ZFTPConnector
     */
    private SCLMSCMRevisionState getNewRevision(SCLMSCMRevisionState baseline,
                                                String user, String password) {
        logger.info(logPrefix + "Will get new Revision State.");

        // Construct connector.
        ZFTPConnector zFTPConnector = new ZFTPConnector(
                this.server,
                this.port,
                user, password, this.JESINTERFACELEVEL1, logPrefix, this.FTPActiveMode);

        // Fetch revision.
        return new SCLMSCMRevisionState(this.project, this.alternate, this.group, this.types, this.JobHeader + "\n" + this.JobStep, zFTPConnector, baseline);
    }

    /**
     * Whether SCM supports polling.
     *
     * @return <code>true</code>
     */
    @Override
    public boolean supportsPolling() {
        return true;
    }

    /**
     * Whether SCM requires workspace for polling process.
     *
     * @return <code>false</code>
     */
    @Override
    public boolean requiresWorkspaceForPolling() {
        return true;
    }

    /**
     * Compare remote revision with old one.
     *
     * @param project   Current project.
     * @param launcher  Current launcher.
     * @param workspace Current workspace.
     * @param listener  Current listener.
     * @param _baseline Old revision.
     * @return PollingResult with comparison.
     * @see PollingResult
     * @see SCLMSCMRevisionState
     * @see SCLMSCM#getNewRevision(SCLMSCMRevisionState, String, String)
     */
    @Override
    public PollingResult compareRemoteRevisionWith(@Nonnull Job<?, ?> project, Launcher launcher, FilePath workspace, @Nonnull TaskListener listener, @Nonnull SCMRevisionState _baseline) {
        Run<?, ?> lastRun = project.getLastBuild();
        // Get login + pw.
        DomainRequirement domain = new DomainRequirement();
        StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById(credentialsId,
                StandardUsernamePasswordCredentials.class, lastRun,
                domain);
        if (creds == null) {
            listener.getLogger().println("Cannot resolve credentials: " + credentialsId);
            return PollingResult.NO_CHANGES;
        }

        // Get new revision.
        SCLMSCMRevisionState baseline = (SCLMSCMRevisionState) _baseline;
        SCLMSCMRevisionState tempRevision = this.getNewRevision(baseline,
                creds.getUsername(), creds.getPassword().getPlainText());

        // Compare cached state with latest polled state.
        boolean changes = !tempRevision.getChangedOnly().isEmpty();

        // Return a PollingResult to tell Jenkins whether to checkout and build or not.
        return new PollingResult(baseline, tempRevision, changes ? PollingResult.Change.SIGNIFICANT : PollingResult.Change.NONE);
    }

    /**
     * Checkout remote changes to the workspace.
     * <br>As the build itself is performed via SCLM, the checkout's main task is generation of revision.
     *
     * @param build         Current build.
     * @param launcher      Current launcher.
     * @param workspace     Current workspace.
     * @param listener      Current listener.
     * @param changelogFile Current changeLogFile.
     * @param baseline      Last revision.
     * @see SCLMSCMRevisionState
     * @see SCLMSCM#getNewRevision(SCLMSCMRevisionState, String, String)
     */
    @Override
    public void checkout(@Nonnull Run<?, ?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace, @Nonnull TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException {
        logger.info(logPrefix + "Will checkout");
        // Get login + pw.
        DomainRequirement domain = new DomainRequirement();
        StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById(credentialsId,
                StandardUsernamePasswordCredentials.class, build,
                domain);
        if (creds == null) {
            listener.getLogger().println("Cannot resolve credentials: " + credentialsId);
            this.createEmptyChangeLog(changelogFile, listener, "changelog");
            return;
        }

        // Get new revision.
        this.currentRevision = this.getNewRevision((SCLMSCMRevisionState) baseline,
                creds.getUsername(), creds.getPassword().getPlainText());

        if (changelogFile != null) {
            // Need to write changelog.xml.
            // Narrow file list and write it.
            List<SCLMFileState> temp = this.currentRevision.getChangedOnly();
            if (!temp.isEmpty()) {
                temp.sort(SCLMFileState.changeComparator);
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(changelogFile), StandardCharsets.UTF_8));
                writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                writer.println("<changelog>");
                for (SCLMFileState file : temp) {
                    String editType;
                    editType = (file.editType == null) ? "SAME" : file.editType.getName().toUpperCase();
                    String entryText = String.format(entryTemplateString,
                            SCLMFileState.dateToString(file.changeDate),
                            file.project,
                            file.alternate,
                            file.group,
                            file.type,
                            file.name,
                            file.version,
                            file.changeUserID,
                            file.changeGroup,
                            editType);
                    writer.println(entryText);
                }
                writer.println("</changelog>");
                writer.close();
            } else {
                this.createEmptyChangeLog(changelogFile, listener, "changelog");
            }
        }
    }

    /**
     * Calculate revision from build. Dummy.
     *
     * @param build     Current build.
     * @param workspace Current workspace.
     * @param launcher  Current launcher.
     * @param listener  Current listener.
     * @return Actual revision.
     * @see SCLMSCMRevisionState
     */
    @Override
    public SCMRevisionState calcRevisionsFromBuild(@Nonnull Run<?, ?> build, FilePath workspace, Launcher launcher, @Nonnull TaskListener listener) {
        // Remove 'DELETED' files from revision.
        this.currentRevision.removeDeleted();
        return this.currentRevision;
    }

    /**
     * Get parser for changelog.xml
     * .
     *
     * @return SCLMChangeLogParser instance.
     */
    @Override
    public ChangeLogParser createChangeLogParser() {
        return new SCLMChangeLogParser();
    }

    /**
     * Get descriptor.
     *
     * @return Descriptor for SCLMSCM.
     */
    @Override
    public SCLMSCMDescriptor getDescriptor() {
        return (SCLMSCMDescriptor) super.getDescriptor();
    }

    /**
     * <h2>SCLMSCMDescriptor</h2>
     * Descriptor for SCLMSCM.
     *
     * @author Alexander Shcherbakov (candiduslynx@gmail.com)
     * @version 1.0
     */
    @Extension
    public static final class SCLMSCMDescriptor extends SCMDescriptor<SCLMSCM> {
        /**
         * Default job header.
         */
        private static final String DEFAULT_SCLM_JOB_HEADER =
                "//JENKINS  JOB (ACCOUNT),'JENKINS',                             \n" +
                        "// MSGCLASS=A,CLASS=A,NOTIFY=&SYSUID";
        /**
         * Default step for FLMCMD invocation.
         */
        private static final String DEFAULT_SCLM_JOB_STEP =
                "//SCLMEX   EXEC PGM=IKJEFT01,REGION=4096K,TIME=1439,DYNAMNBR=200\n" +
                        "//STEPLIB  DD DSN=ISP.SISPLPA,DISP=SHR                          \n" +
                        "//         DD DSN=ISP.SISPLOAD,DISP=SHR                         \n" +
                        "//ISPMLIB  DD DSN=ISP.SISPMENU,DISP=SHR                         \n" +
                        "//ISPSLIB  DD DSN=ISP.SISPSENU,DISP=SHR                         \n" +
                        "//         DD DSN=ISP.SISPSLIB,DISP=SHR                         \n" +
                        "//ISPPLIB  DD DSN=ISP.SISPPENU,DISP=SHR                         \n" +
                        "//ISPTLIB  DD UNIT=@TEMP0,DISP=(NEW,PASS),SPACE=(CYL,(1,1,5)),  \n" +
                        "//            DCB=(LRECL=80,BLKSIZE=19040,DSORG=PO,RECFM=FB),   \n" +
                        "//            DSN=                                              \n" +
                        "//         DD DSN=ISP.SISPTENU,DISP=SHR                         \n" +
                        "//ISPTABL  DD UNIT=@TEMP0,DISP=(NEW,PASS),SPACE=(CYL,(1,1,5)),  \n" +
                        "//            DCB=(LRECL=80,BLKSIZE=19040,DSORG=PO,RECFM=FB),   \n" +
                        "//            DSN=                                              \n" +
                        "//ISPPROF  DD UNIT=@TEMP0,DISP=(NEW,PASS),SPACE=(CYL,(1,1,5)),  \n" +
                        "//            DCB=(LRECL=80,BLKSIZE=19040,DSORG=PO,RECFM=FB),   \n" +
                        "//            DSN=                                              \n" +
                        "//ISPLOG   DD SYSOUT=*,                                         \n" +
                        "//            DCB=(LRECL=120,BLKSIZE=2400,DSORG=PS,RECFM=FB)    \n" +
                        "//ISPCTL1  DD DISP=NEW,UNIT=@TEMP0,SPACE=(CYL,(1,1)),           \n" +
                        "//            DCB=(LRECL=80,BLKSIZE=800,RECFM=FB)               \n" +
                        "//SYSTERM  DD SYSOUT=*                                          \n" +
                        "//SYSPROC  DD DSN=ISP.SISPCLIB,DISP=SHR                         \n" +
                        "//FLMMSGS  DD SYSOUT=(*)                                        \n" +
                        "//PASCERR  DD SYSOUT=(*)                                        \n" +
                        "//ZFLMDD   DD  *                                                \n" +
                        "   ZFLMNLST=FLMNLENU    ZFLMTRMT=ISR3278    ZDATEF=YY/MM/DD     \n" +
                        "/*                                                              \n" +
                        "//SYSPRINT DD SYSOUT=(*)                                        \n" +
                        "//SYSTSPRT DD SYSOUT=(*)";
        /**
         * Globally configured default job header.
         */
        private static String SCLMJobHeader = DEFAULT_SCLM_JOB_HEADER;
        /**
         * Globally configured FLMCMD job step.
         */
        private static String SCLMJobStep = DEFAULT_SCLM_JOB_STEP;

        /**
         * Dummy constructor.
         */
        public SCLMSCMDescriptor() {
            super(SCLMSCM.class, null);
        }

        /**
         * Get globally configured job header.
         *
         * @return <b><code>SCLMJobHeader</code></b>.
         */
        public String getSCLMJobHeader() {
            if (SCLMJobHeader.isEmpty())
                return SCLMSCMDescriptor.DEFAULT_SCLM_JOB_HEADER;
            else
                return SCLMJobHeader;
        }

        /**
         * Get globally configured FLMCMD job step.
         *
         * @return <b><code>SCLMJobStep</code></b>.
         */
        public String getSCLMJobStep() {
            if (SCLMJobStep.isEmpty())
                return SCLMSCMDescriptor.DEFAULT_SCLM_JOB_STEP;
            else
                return SCLMJobStep;
        }

        /**
         * Fill in credentials ID.
         *
         * @param item
         * @param credentialsId
         * @return
         */
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter String credentialsId) {
            ListBoxModel creds = PermissionsChecker.FillBasicCredentials(item, credentialsId);
            if (creds != null) {
                return creds;
            }
            return new StandardListBoxModel()
                    .includeMatchingAs(
                            item instanceof hudson.model.Queue.Task
                                    ? Tasks.getAuthenticationOf((hudson.model.Queue.Task) item)
                                    : ACL.SYSTEM,
                            item,
                            StandardUsernamePasswordCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));

        }

        /**
         * @param item  configuration entity to use permissions from.
         * @param value Current credentials (or expression/env variable).
         * @return Whether creds are OK. Currently just check that it's set.
         */
        public FormValidation doCheckCredentialsId(
                @AncestorInPath Item item,
                @QueryParameter String value) {
            FormValidation resp = PermissionsChecker.CheckCredentialsID(item, value);
            if (resp != null) {
                return resp;
            }
            List<DomainRequirement> domainRequirements = new ArrayList<>();
            if (CredentialsProvider.listCredentials(
                    StandardUsernamePasswordCredentials.class,
                    item,
                    item instanceof hudson.model.Queue.Task
                            ? Tasks.getAuthenticationOf((Queue.Task) item)
                            : ACL.SYSTEM, domainRequirements,
                    CredentialsMatchers.withId(value)).isEmpty()) {
                return FormValidation.error("Cannot find currently selected credentials");
            }
            return FormValidation.ok();
        }


        /**
         * Get printable name.
         *
         * @return "SCLM".
         */
        @Override
        public String getDisplayName() {
            return "SCLM";
        }

        /**
         * Configure action that is invoked from global settings.
         *
         * @param req  Request.
         * @param json Parameters.
         * @return Whether everything was setup OK.
         */
        @Override
        public boolean configure(org.kohsuke.stapler.StaplerRequest req,
                                 net.sf.json.JSONObject json) {
            SCLMJobHeader = Util.fixEmptyAndTrim(req.getParameter("SCLMJobHeader"));
            if (SCLMJobHeader == null)
                SCLMJobHeader = SCLMSCMDescriptor.DEFAULT_SCLM_JOB_HEADER;
            SCLMJobStep = Util.fixEmptyAndTrim(req.getParameter("SCLMJobStep"));
            if (SCLMJobStep == null)
                SCLMJobStep = SCLMSCMDescriptor.DEFAULT_SCLM_JOB_STEP;
            save();
            return true;
        }
    }
}
