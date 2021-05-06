package org.jenkinsci.plugins.IBM_zOS_Connector;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.*;
import hudson.model.*;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * <h2>ZOSJobSubmitter</h2>
 * Build step action for submitting JCL job.
 *
 * @author <a href="mailto:candiduslynx@gmail.com">Alexander Shcherbakov</a>
 * @version 1.0
 */
public class ZOSJobSubmitter extends Builder implements SimpleBuildStep {
    /**
     * Simple logger.
     */
    private static final Logger logger = Logger.getLogger(ZOSJobSubmitter.class.getName());
    /**
     * LPAR name or IP address.
     */
    private final String server;
    /**
     * FTP port for connection
     */
    private final int port;
    /**
     * Credentials id to be converted to login+pw.
     */
    private final String credentialsId;
    /**
     * Whether need to wait for the job completion.
     */
    private final boolean wait;
    /**
     * Whether FTP server is in JESINTERFACELEVEL=1.
     */
    private final boolean JESINTERFACELEVEL1;
    /**
     * Whether the job log is to be deleted upon job end.
     */
    private final boolean deleteJobFromSpool;
    /**
     * Whether the job log is to be printed to Console.
     */
    private final boolean jobLogToConsole;
    /**
     * Time to wait for the job to end. If set to <code>0</code> the buil will wait forever.
     */
    private final int waitTime;
    /**
     * Path to local file with JCL text of the job to be submitted.
     */
    private final String jobFile;
    /**
     * MaxCC to decide that job ended OK.
     */
    private String MaxCC;
    /**
     * FTP data transfer mode
     */
    private final boolean FTPActiveMode;

    /**
     * Constructor. Invoked when 'Apply' or 'Save' button is pressed on the project configuration page.
     *
     * @param MaxCC              Maximum allowed CC for job to be considered OK.
     * @param server             LPAR name or IP address.
     * @param port               FTP port to connect to.
     * @param credentialsId      Credentials id..
     * @param wait               Whether we need to wait for the job completion.
     * @param waitTime           Maximum wait time. If set to <code>0</code> will wait forever.
     * @param deleteJobFromSpool Whether the job log will be deleted from the spool after end.
     * @param jobLogToConsole    Whether the job log will be printed to console.
     * @param jobFile            File with JCL of the job to be submitted.
     * @param JESINTERFACELEVEL1 Is FTP server configured for JESINTERFACELEVEL=1?
     * @param FTPActiveMode      FTP data transfer mode (true=active, false=passive)       
     */
    @DataBoundConstructor
    public ZOSJobSubmitter(
            String server,
            int port,
            String credentialsId,
            boolean wait,
            int waitTime,
            boolean deleteJobFromSpool,
            boolean jobLogToConsole,
            String jobFile,
            String MaxCC,
            boolean JESINTERFACELEVEL1,
            boolean FTPActiveMode) {
        // Copy values
        this.server = server.replaceAll("\\s", "");
        this.port = port;
        this.credentialsId = credentialsId;
        this.wait = wait;
        this.waitTime = waitTime;
        this.JESINTERFACELEVEL1 = JESINTERFACELEVEL1;
        this.FTPActiveMode = FTPActiveMode;
        this.deleteJobFromSpool = deleteJobFromSpool;
        this.jobLogToConsole = jobLogToConsole;
        this.jobFile = jobFile;
        if (MaxCC == null || MaxCC.isEmpty()) {
            this.MaxCC = "0000";
        } else {
            this.MaxCC = MaxCC;
            if (this.MaxCC.length() < 4) {
                this.MaxCC = "000".substring(0, 4 - this.MaxCC.length()) + this.MaxCC;
            }
        }
    }

    /**
     * Submit the job for execution.
     *
     * @param run       Current run
     * @param workspace Current workspace
     * @param launcher  Current launcher
     * @param listener  Current listener
     *                  <p>
     *                  <br> Always <code>true</code> if <b><code>wait</code></b> is <code>false</code>.
     * @see ZFTPConnector
     */
    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener)
            throws IOException {
        // variables to be expanded
        String _server = this.server;
        String _jobFile = this.jobFile;
        String _MaxCC = this.MaxCC;
        String inputJCL;

        String logPrefix = run.getParent().getDisplayName() + " " + run.getId() + ": ";
        try {
            logger.info(logPrefix + "will expand variables");
            EnvVars environment = run.getEnvironment(listener);
            _server = environment.expand(_server);
            _jobFile = environment.expand(_jobFile);
            _MaxCC = environment.expand(_MaxCC);
            // Read the JCL + expand.
            try {
                inputJCL = workspace.child(_jobFile).readToString();
            } catch (FileNotFoundException e) {
                throw new AbortException("Job file not found: ./" + _jobFile);
            }
            inputJCL = environment.expand(inputJCL);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new AbortException(e.getMessage());
        }

        // Get login + pw.
        DomainRequirement domain = new DomainRequirement();
        StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById(credentialsId,
                StandardUsernamePasswordCredentials.class,
                run, domain);
        if (creds == null) {
            throw new AbortException("Cannot resolve credentials: " + credentialsId);
        }

        // Prepare the input and output stream.
        ByteArrayInputStream inputStream = new ByteArrayInputStream(inputJCL.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Get connector.
        ZFTPConnector zFTPConnector = new ZFTPConnector(_server,
                this.port,
                creds.getUsername(),
                creds.getPassword().getPlainText(),
                this.JESINTERFACELEVEL1,
                logPrefix,
                this.FTPActiveMode);
        // Submit the job.
        boolean result = zFTPConnector.submit(inputStream, this.wait, this.waitTime, outputStream, this.deleteJobFromSpool, listener);

        // Get CC.
        String printableCC = zFTPConnector.getJobCC();
        if (printableCC != null)
            printableCC = printableCC.replaceAll("\\s+", "");
        else
            printableCC = "";

        // Print the info about the job
        logger.info("Job [" + zFTPConnector.getJobID() + "] processing finished.");
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("Job [")
                .append(zFTPConnector.getJobID())
                .append("] processing ");
        if (this.wait) {
            if (!printableCC.matches("\\d+")) {
                if (printableCC.startsWith("ABEND")) {
                    reportBuilder.append("ABnormally ENDed. ABEND code = [");
                } else {
                    reportBuilder.append("failed. Reason: [");
                }
            } else {
                reportBuilder.append("finished. Captured RC = [");
            }
            reportBuilder
                    .append(printableCC)
                    .append("]");
        } else {
            reportBuilder.append("finished. Skip waiting.");
        }
        listener.getLogger().println(reportBuilder);

        // If wait was requested try to save the job log.
        if (this.wait) {
            if (this.jobLogToConsole) {
                listener.getLogger().println(outputStream.toString(StandardCharsets.US_ASCII.name()));
            }
            // Save the log.
            try {
                FilePath savedOutput = new FilePath(workspace,
                        String.format("%s [%s] (%s - %s) %s - %s.log",
                                zFTPConnector.getJobName(),
                                printableCC,
                                _server,
                                zFTPConnector.getJobID(),
                                run.getParent().getDisplayName(),
                                run.getId()
                        ));
                outputStream.writeTo(savedOutput.write());
                outputStream.close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                throw new AbortException(e.getMessage());
            }
        } else {
            printableCC = "0000"; //set RC = 0
        }

        if (!(result && (_MaxCC.compareTo(printableCC) >= 0))) {
            throw new AbortException("z/OS job failed with CC " + printableCC);
        }
    }

    /**
     * Get LPAR name of IP address.
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
     * @return job file provided.
     */
    public String getJobFile() {
        return jobFile;
    }

    /**
     * Get wait.
     *
     * @return <b><code>wait</code></b>
     */
    public boolean getWait() {
        return this.wait;
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
     * Get deleteJobFromSpool.
     *
     * @return <b><code>deleteJobFromSpool</code></b>
     */
    public boolean getDeleteJobFromSpool() {
        return this.deleteJobFromSpool;
    }

    /**
     * Get jobLogToConsole.
     *
     * @return <b><code>jobLogToConsole</code></b>
     */
    public boolean getJobLogToConsole() {
        return this.jobLogToConsole;
    }

    /**
     * Get wait time.
     *
     * @return <b><code>waitTime</code></b>
     */
    public int getWaitTime() {
        return this.waitTime;
    }


    /**
     * @return <b><code>MaxCC of the job to be considered OK</code></b>
     */
    public String getMaxCC() {
        return this.MaxCC;
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
     * Get descriptor for this class.
     *
     * @return descriptor for this class.
     */
    @Override
    public ZOSJobSubmitterDescriptor getDescriptor() {
        return (ZOSJobSubmitterDescriptor) super.getDescriptor();
    }

    /**
     * <h2>zOSJobSubmitterDescriptor</h2>
     * Descriptor for ZOSJobSubmitter.
     *
     * @author Alexander Shchrbakov (candiduslynx@gmail.com)
     * @version 1.0
     */
    @Extension
    public static final class ZOSJobSubmitterDescriptor extends BuildStepDescriptor<Builder> {
        /**
         * Primitive constructor.
         */
        public ZOSJobSubmitterDescriptor() {
            load();
        }

        /**
         * Function for validation of 'Server' field on project configuration page
         *
         * @param value Current server.
         * @return Whether server name looks OK.
         */
        public FormValidation doCheckServer(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please set a server");
            return FormValidation.ok();
        }


        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter String credentialsId) {
            ListBoxModel creds = PermissionsChecker.FillBasicCredentials(item, credentialsId);
            if (creds != null) {
                return creds;
            }
            return new StandardListBoxModel()
                    .includeMatchingAs(
                            item instanceof Queue.Task
                                    ? Tasks.getAuthenticationOf((Queue.Task) item)
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
                    item instanceof Queue.Task
                            ? Tasks.getAuthenticationOf((Queue.Task) item)
                            : ACL.SYSTEM, domainRequirements,
                    CredentialsMatchers.withId(value)).isEmpty()) {
                return FormValidation.error("Cannot find currently selected credentials");
            }
            return FormValidation.ok();
        }

        /**
         * Function for validation of 'Job file' field on project configuration page
         *
         * @param value Current job file.
         * @return Whether job looks OK.
         */
        public FormValidation doCheckJobFIle(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please set an input");
            return FormValidation.ok();
        }

        /**
         * Function for validation of 'Wait Time' field on project configuration page
         *
         * @param value Current wait time.
         * @return Whether wait time looks OK.
         */
        public FormValidation doCheckWaitTime(@QueryParameter String value) {
            if (!value.matches("\\d*"))
                return FormValidation.error("Value must be numeric");
            if (Integer.parseInt(value) < 0)
                return FormValidation.error("Value must not be negative");
            return FormValidation.ok();
        }

        public FormValidation doCheckMaxCC(@QueryParameter String value) {
            if (!value.matches("(\\d{1,4})|(\\s*)"))
                return FormValidation.error("Value must be 4 decimal digits or empty");
            return FormValidation.ok();

        }

        /**
         * If this build step can be used with the project.
         *
         * @param aClass Project description class.
         * @return Always <code>true</code>.
         */
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * Get printable name.
         *
         * @return Printable name for project configuration page.
         */
        public String getDisplayName() {
            return "Submit z/OS job";
        }
    }
}