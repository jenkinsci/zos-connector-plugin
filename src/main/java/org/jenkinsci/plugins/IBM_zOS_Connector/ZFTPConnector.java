package org.jenkinsci.plugins.IBM_zOS_Connector;

import hudson.model.TaskListener;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * <h1>ZFTPConnector</h1>
 * FTP-based communication with z/OS-like systems.
 * Used for submitting jobs, fetching job log and extraction of MaxCC.
 *
 * @author <a href="mailto:candiduslynx@gmail.com">Alexander Shcherbakov</a>
 * @version 1.0
 */
class ZFTPConnector {
    // Server info.
    /**
     * Will ask LPAR once in 10 seconds.
     */
    private static final long waitInterval = 10 * 1000;
    /**
     * Pattern for search of jobName
     */
    private static final Pattern JesJobName = Pattern.compile("250-It is known to JES as (.*)");

    // Credentials.
    /**
     * Simple logger.
     */
    private static final Logger logger = Logger.getLogger(ZFTPConnector.class.getName());
    /**
     * LPAR name or IP to connect to.
     */
    private String server;

    // Wait parameters.
    /**
     * FTP port for connection
     */
    private int port;

    // Job info from JES-like system.
    /**
     * UserID.
     */
    private String userID;
    /**
     * User password.
     */
    private String password;
    /**
     * Time to wait before giving up in milliseconds. If set to <code>0</code> will wait forever.
     */
    private long waitTime;
    /**
     * JobID in JES.
     */
    private String jobID;
    /**
     * Whether job log was successfully captured
     */
    private boolean jobLogCaptured;

    // Work elements.
    /**
     * Jobname in JES.
     */
    private String jobName;
    /**
     * Job's MaxCC.
     */
    private String jobCC;
    // JESINTERFACELEVEL=1
    private boolean JESINTERFACELEVEL1;
    /**
     * FTPClient from <i>Apache Commons-Net</i>. Used for FTP communication.
     */
    private FTPClient FTPClient;
    /**
     * Log prefix (default: "ZFTPConnector")
     */
    private String logPrefix;
    /**
     * Task listener (if provided).
     */
    private TaskListener listener;
    /**
     * FTP transfer mode
     */
    private boolean FTPActiveMode;

    /**
     * Basic constructor with minimal parameters required.
     *
     * @param server             LPAR name or IP address to connect to.
     * @param port               LPAR password.
     * @param userID             UserID.
     * @param password           User password.
     * @param JESINTERFACELEVEL1 Is FTP server configured for JESINTERFACELEVEL=1?
     * @param logPrefix          Log prefix.
     * @param FTPActiveMode      FTP data transfer mode (true=active, false=passive) 
     */
    ZFTPConnector(String server, int port, String userID, String password, boolean JESINTERFACELEVEL1, String logPrefix, boolean FTPActiveMode) {
        // Copy values
        this.server = server;
        this.port = port;
        this.userID = userID;
        this.password = password;
        this.JESINTERFACELEVEL1 = JESINTERFACELEVEL1;
        this.FTPActiveMode = FTPActiveMode; 

        this.FTPClient = null;

        this.logPrefix = "";
        if (logPrefix != null)
            this.logPrefix = logPrefix;
        this.log("Created ZFTPConnector");

        this.listener = null;
    }

    /**
     * Try to connect to the <b><code>server</code></b> using the parameters passed to the constructor.
     *
     * @return Whether the connection was established using the parameters passed to the constructor.
     * @see ZFTPConnector#ZFTPConnector(String, int, String, String, boolean, String)
     */
    private boolean connect() {
        // 1. Disconnect and ignore error.
        try {
            this.FTPClient.disconnect();
        } catch (IOException ignored) {

        }
        // Perform the connection.
        try {
            int reply; // Temp value to contain server response.

            // Try to connect.
            this.FTPClient.connect(this.server, this.port);

            // After connection attempt, check the reply code to verify success.
            reply = this.FTPClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                // Bad reply code.
                this.FTPClient.disconnect(); // Disconnect from LPAR.
                this.err("FTP server refused connection."); // Print error.
                return false; // Finish with failure.
            }
            this.log("FTP: connected to " + server + ":" + port);
        }
        // IOException handling
        catch (IOException e) {
            // Close the connection if it's still open.
            if (this.FTPClient.isConnected()) {
                try {
                    this.FTPClient.disconnect();
                } catch (IOException f) {
                    // Do nothing
                }
            }
            this.err("Could not connect to server.");
            e.printStackTrace();
            return false;
        }
        // Finally, return with success.
        return true;
    }

    /**
     * Try to logon to the <b><code>server</code></b> using the parameters passed to the constructor.
     * Also, <code>site filetype=jes jesjobname=* jesowner=*</code> command is invoked.
     *
     * @return Whether the credentials supplied are valid and the connection was established.
     * @see ZFTPConnector#ZFTPConnector(String, int, String, String, boolean, String)
     * @see ZFTPConnector#connect()
     */
    private boolean logon() {
        // 1. log out, ignore error
        try {
            this.FTPClient.logout();
        } catch (IOException ignored) {
        }

        // Check whether we are already connected. If not, try to reconnect.
        if (!this.connect())
            return false; // Couldn't connect to the server. Can't check the credentials.

        // Perform the login process.
        try {
            int reply; // Temp value for server reply code.

            // Try to login.
            if (!this.FTPClient.login(this.userID, this.password)) {
                // If couldn't login, we should logout and return failure.
                this.FTPClient.logout();
                return false;
            }

            // Try to set filetype, jesjobname and jesstatus.
            this.FTPClient.site("filetype=jes jesjobname=* jesstatus=ALL");
            // Check reply.
            reply = this.FTPClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                this.FTPClient.disconnect();
                this.err("FTP server refused to change FileType and JESJobName.");
                return false;
            }
        } catch (IOException e) {
            if (this.FTPClient.isConnected()) {
                try {
                    this.FTPClient.disconnect();
                } catch (IOException f) {
                    // do nothing
                }
            }
            this.err("Could not logon to server.");
            e.printStackTrace();
            return false;
        }

        // If go here, everything went fine.
        return true;
    }

    /**
     * Try logging out of the FTP server.
     * This will not fail at all - instead if the next relogon attempt fails you will see something more accurate.
     */
    private void disconnect() {
        if (this.FTPClient == null)
            return;
        try {
            this.FTPClient.logout();
        } catch (IOException ignored) {
        } finally {
            try {
                this.FTPClient.disconnect();
            } catch (IOException ignored) {
            }
        }
    }

    boolean submit(InputStream inputStream, boolean wait, int waitTime, OutputStream outputStream, boolean deleteLogFromSpool, TaskListener taskListener) {
        this.listener = taskListener;
        return this.submit(inputStream, wait, waitTime, outputStream, deleteLogFromSpool);
    }

    /**
     * Submit job for execution.
     *
     * @param inputStream        JCL text of the job.
     * @param wait               Whether we need for the job to complete.
     * @param waitTime           Maximum wait time in minutes. If set to <code>0</code>, will wait forever.
     * @param outputStream       Stream to put job log. Can be <code>Null</code>.
     * @param deleteLogFromSpool Whether the job log should be deleted fro spool upon job end.
     * @return Whether the job was successfully submitted and the job log was fetched.
     * <br><b><code>jobCC</code></b> holds the response of the operation (including errors).
     * @see ZFTPConnector#logon()
     * @see ZFTPConnector#waitForCompletion(OutputStream)
     * @see ZFTPConnector#deleteJobLog()
     */
    boolean submit(InputStream inputStream, boolean wait, int waitTime, OutputStream outputStream, boolean deleteLogFromSpool) {
        this.waitTime = ((long) waitTime) * 60 * 1000; // Minutes to milliseconds.

        // Clean-up
        this.jobID = "";
        this.jobName = "";
        this.jobCC = "";
        this.jobLogCaptured = false;

        // Create FTPClient
        this.FTPClient = new FTPClient();
        // Make password invisible from log
        this.FTPClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)), true));


        // Verify connection.
        if (!this.logon()) {
            this.disconnect();
            this.jobCC = "COULD_NOT_CONNECT";
            return false;
        }

        try {
            // Submit the job.
        	if (!this.FTPActiveMode) {
        		this.FTPClient.enterLocalPassiveMode();
        	}
            this.FTPClient.storeFile("jenkins.sub", inputStream);

            // Scan reply from server to get JobID.
            for (String s : this.FTPClient.getReplyStrings()) {
                Matcher matcher = JesJobName.matcher(s);
                if (matcher.matches()) {
                    // Set jobID
                    this.jobID = matcher.group(1);
                    break;
                }
            }
            if (this.jobID.isEmpty()) {
                this.err("Failed to parse JES job ID. Response lines:---->\n");
                Arrays.stream(this.FTPClient.getReplyStrings()).forEachOrdered(this::err);
                this.err("Failed to parse JES job ID. Response lines:<----\n");
                this.jobCC = "FAILED_TO_PARSE_JOB_ID";
                return false;
            }
            this.log("Submitted job [" + this.jobID + "]");
            inputStream.close();
        } catch (FTPConnectionClosedException e) {
            this.err("Server closed connection.");
            e.printStackTrace();
            this.jobCC = "SERVER_CLOSED_CONNECTION";
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            this.jobCC = "IO_ERROR";
            return false;
        }

        if (wait) {
            // Wait for completion.
            if (this.waitForCompletion(outputStream)) {
                if (deleteLogFromSpool) {
                    // Delete job log from spool.
                    this.deleteJobLog();
                }
                this.disconnect();
                return true;
            } else {
                if (this.jobCC == null)
                    this.jobCC = "JOB_DID_NOT_FINISH_IN_TIME";
                this.disconnect();
                return false;
            }
        }

        // If we are here, everything went fine.
        this.disconnect();
        return true;
    }

    /**
     * Wait for he completion of the job.
     *
     * @param outputStream Stream to hold job log.
     * @return Whether the job finished in time.
     * @see ZFTPConnector#submit(InputStream, boolean, int, OutputStream, boolean)
     * @see ZFTPConnector#fetchJobLog(OutputStream)
     */
    private boolean waitForCompletion(OutputStream outputStream) {
        // Initialize current time and estimated time.
        long curr = System.currentTimeMillis();
        long jobEndTime = curr + this.waitTime;
        boolean eternal = (waitTime == 0);
        boolean jobWasObserved = false;

        // Perform wait
        do {
            // Wait
            try {
                Thread.sleep(waitInterval);
                curr = System.currentTimeMillis();
            } catch (InterruptedException e) {
                this.err("Interrupted.");
                this.jobCC = "WAIT_INTERRUPTED";
                return false;
            }

            // check job state
            if (this.checkJobAvailability()) {
                jobWasObserved = true;
            } else {
                if (jobWasObserved) {
                    return false;
                }
            }
            // Try to fetch job log.
            if (this.fetchJobLog(outputStream))
                return true;
        } while (eternal || (curr <= jobEndTime));

        // Exit with wait error.
        this.jobCC = "WAIT_ERROR";
        return false;
    }

    /**
     * @return true if job can be listed through FTP.
     */
    private boolean checkJobAvailability() {
    	if (!this.FTPActiveMode) {
    		this.FTPClient.enterLocalPassiveMode();
    	}
        // Verify connection.
        if (!this.logon()) {
            this.jobCC = "CHECK_JOB_AVAILABILITY_ERROR_LOGIN";
            return false;
        }

        // Try listing files
        try {
            String[] availableJobs = this.FTPClient.listNames("*");
            if (availableJobs == null) {
                this.err("failed to list available jobs");
                return false;
            }
            if (Arrays.stream(availableJobs).anyMatch(name -> this.jobID.equals(name))) {
                return true;
            }
            this.err("Job [" + this.jobID + "] cannot be found in JES");
            this.jobCC = "JOB_NOT_FOUND_IN_JES";
            return false;
        } catch (IOException e) {
            this.jobCC = "CHECK_JOB_AVAILABILITY_IO_ERROR";
            return false;
        }
    }

    /**
     * Fetch job log from spool.
     *
     * @param outputStream Stream to hold the job log.
     * @return Whether the job log was fetched from the LPAR.
     * @see ZFTPConnector#waitForCompletion(OutputStream)
     */
    private boolean fetchJobLog(OutputStream outputStream) {
        // Verify connection.
        if (!this.logon()) {
            this.jobCC = "FETCH_LOG_ERROR_LOGIN";
            return false;
        }

    	if (!this.FTPActiveMode) {
    		this.FTPClient.enterLocalPassiveMode();
    	}
        if (!this.jobLogCaptured) {
            // Try fetching.
            try {
                // Try fetching the log.
                this.jobLogCaptured = this.FTPClient.retrieveFile(this.jobID, outputStream);
                if (!this.jobLogCaptured) {
                    this.jobCC = "RETR_ERR_JOB_NOT_FINISHED_OR_NOT_FOUND";
                    return false;
                }
            } catch (IOException e) {
                this.jobCC = "FETCH_LOG_IO_ERROR";
                return false;
            }
        }
        return this.obtainJobRC();
    }

    /**
     * @return Whether job RC was correctly obtained or not.
     */
    private boolean obtainJobRC() {
        this.jobCC = "COULD_NOT_RETRIEVE_JOB_RC";
        // Verify connection.
        if (!this.logon()) {
            return false;
        }

        // JOB NAME
        Pattern JOBNAME = Pattern.compile("(\\S+)\\s+" + jobID + "\\s+(.*)");

        Pattern CC = Pattern.compile(".* RC=(\\S+) .*");
        Pattern CCUndefined = Pattern.compile(".* RC\\s+(\\S+)\\s+.*");
        Pattern ABEND = Pattern.compile(".* ABEND=(.*?) .*");
        Pattern JCLERROR = Pattern.compile(".* \\(JCL error\\) .*");

    	if (!this.FTPActiveMode) {
    		this.FTPClient.enterLocalPassiveMode();
    	}
        // Check RC.
        try {
            for (FTPFile ftpFile : this.FTPClient.listFiles("*")) {
                String fileName = ftpFile.toString();

                Matcher JOBNAMEMatcher = JOBNAME.matcher(fileName);
                if (JOBNAMEMatcher.matches()) {
                    this.jobName = JOBNAMEMatcher.group(1);
                    this.log("Found job " + this.jobID + " with name " + this.jobName + " in JES");
                    String rcPart = JOBNAMEMatcher.group(2);
                    this.log("Will check JOB status in '" + rcPart + "'");
                    if (this.JESINTERFACELEVEL1) {
                        if (rcPart.startsWith("INPUT")) {
                            this.log("Found job " + jobName + " in INPUT");
                            return false;
                        }
                        if (rcPart.startsWith("ACTIVE")) {
                            this.log("Found job " + jobName + " in ACTIVE");
                            return false;
                        }
                        if (rcPart.startsWith("OUTPUT")) {
                            this.log("Found job " + jobName + " in OUTPUT, will fetch log for additional scan");
                            Pattern HASP395 = Pattern.compile(".*HASP395\\s+" + jobName + "\\s+ENDED(\\s+-\\s+(\\S+)\\s*)?.*");
                            // Try fetching the log.
                            ByteArrayOutputStream tempOutputStream = new ByteArrayOutputStream();
                            boolean gotHASP395 = false;
                            // If we see "JCL ERROR" line before HASP365 without actual RC - use JCL ERROR
                            boolean sawJCLError = false;
                            if (this.FTPClient.retrieveFile(this.jobID, tempOutputStream)) {
                                for (String line : tempOutputStream.toString(StandardCharsets.US_ASCII.name()).split("\\n")) {
                                    sawJCLError |= line.contains("JCL ERROR");
                                    Matcher HASP395Matcher = HASP395.matcher(line);
                                    if (HASP395Matcher.matches()) {
                                        rcPart = HASP395Matcher.group(2);
                                        if (rcPart == null) {
                                            if (sawJCLError) {
                                                this.jobCC = "JCL_ERROR";
                                                return true;
                                            }
                                            this.err("Found HASP395 with no RC info: '" + line + "'");
                                            return false;
                                        }
                                        this.log("Found HASP395: '" + rcPart + "'");
                                        rcPart = "FROM_JOB_LOG " + rcPart + " FROM_JOB_LOG";
                                        gotHASP395 = true;
                                        break;
                                    }
                                }
                            }
                            if (!gotHASP395) {
                                this.err("Failed to find HASP395 in job log");
                                return false;
                            }
                        }
                    }
                    // Here we either have rcPart in JESINTERFACELEVEL=2 format
                    Matcher JCLERRORMatcher = JCLERROR.matcher(rcPart);
                    if (JCLERRORMatcher.matches()) {
                        this.jobCC = "JCL_ERROR";
                        return true;
                    }
                    Matcher ABENDMatcher = ABEND.matcher(rcPart);
                    if (ABENDMatcher.matches()) {
                        this.jobCC = "ABEND_" + ABENDMatcher.group(1);
                        return true;
                    }
                    Matcher CCUndefinedMatcher = CCUndefined.matcher(rcPart);
                    if (CCUndefinedMatcher.matches()) {
                        this.jobCC = CCUndefinedMatcher.group(1).toUpperCase();
                        return true;
                    }
                    Matcher CCMatcher = CC.matcher(rcPart);
                    if (CCMatcher.matches()) {
                        this.jobCC = CCMatcher.group(1);
                        return true;
                    }
                    this.err("Unexpected rc part: '" + rcPart + "'");

                    return false;
                }
            }
        } catch (IOException ignored) {
            // Do nothing.
        }
        return false;
    }

    /**
     * Delete job log from spool. Job is distinguished by previously obtained <code>jobID</code>.
     *
     * @see ZFTPConnector#submit(InputStream, boolean, int, OutputStream, boolean)
     */
    private void deleteJobLog() {
        // Verify connection.
        if (!this.logon()) {
            return;
        }

        // Delete log.
    	if (!this.FTPActiveMode) {
    		this.FTPClient.enterLocalPassiveMode();
    	}
        try {
            this.FTPClient.deleteFile(this.jobID);
        } catch (IOException e) {
            // Do nothing.
        }
    }

    /**
     * Get JobID.
     *
     * @return Current <b><code>jobID</code></b>.
     */
    String getJobID() {
        return this.jobID;
    }

    /**
     * Get Jobname.
     *
     * @return Current <b><code>jobName</code></b>.
     */
    String getJobName() {
        return this.jobName;
    }

    /**
     * Get JobCC.
     *
     * @return Current <b><code>jobCC</code></b>.
     */
    String getJobCC() {
        return this.jobCC;
    }

    /**
     * Log information into logger.info and listener logger
     *
     * @param text Text for logging.
     */
    private void log(String text) {
        logger.info(logPrefix + text);
        System.out.println(text);
        if (listener != null)
            listener.getLogger().println(text);
    }

    /**
     * Log information into logger.severe and listener error logger
     *
     * @param text Text for logging.
     */
    private void err(String text) {
        logger.severe(logPrefix + text);
        System.err.println(text);
        if (listener != null)
            listener.error(text);
    }
}