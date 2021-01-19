package com.tirediscounters.etl.phone

import com.tirediscounters.etl.common.APIETLApplication
import com.tirediscounters.etl.common.model.EmployeeKey
import com.tirediscounters.etl.common.model.StoreKey
import com.tirediscounters.etl.dbreader.RedshiftReader
import com.tirediscounters.etl.common.model.RichResultSet
import com.tirediscounters.etl.common.S3ObjectHandle
import com.tirediscounters.utils.ProgramArguments
import com.tirediscounters.utils.ProgramEnvironment
import groovy.sql.Sql
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.core.env.Environment
import javax.sql.DataSource
import java.net.*
import groovy.json.JsonSlurper
import java.nio.charset.StandardCharsets
import java.util.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import com.tirediscounters.etl.phone.model.PhoneCall
import com.tirediscounters.etl.phone.model.ReportHeader

@SpringBootApplication (
        excludeName = ["org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
        ])
class PhoneEtlApplication extends APIETLApplication implements CommandLineRunner {
    public static final Logger LOGGER = LoggerFactory.getLogger(PhoneEtlApplication);
    private static final DateTimeFormatter ISOLOCALDATEFORMAT = DateTimeFormatter.ISO_LOCAL_DATE

    public static final String DW_PHONE_REPORT_DESCRIPTION = 'Historic Call List for DW'

    @Autowired
    private Environment environment;

    private String akixiApiUrl
	private String akixiUsername
    private String akixiPassword

    public String sessionId

    static void main(String[] args){
        SpringApplication.run(PhoneEtlApplication, args)
    }

    @Override
    public void run(String... args) {
        ProgramArguments programArguments = new ProgramArguments(args)

		ProgramEnvironment programEnvironment = new ProgramEnvironment(environment)

		akixiUsername = programEnvironment.getRequiredPropertyAsString("akixi.phone.username")
		akixiPassword = programEnvironment.getRequiredPropertyAsString("akixi.phone.pw")
		akixiApiUrl = programEnvironment.getRequiredPropertyAsString("akixi.phone.url")
        
	    if (programArguments.getArgumentAsString("localPath").isPresent()) {
            this.m_localPath = programArguments.getArgumentAsString("localPath").get()
        }

        init(programEnvironment)

        this.sessionId = this.login(akixiUsername, akixiPassword)

        List<ReportHeader> reports = this.getReports()

        String historicalCallListId = (reports.find{ it.Description == DW_PHONE_REPORT_DESCRIPTION }).ID

        def reportBody = this.runReport(historicalCallListId)

        this.logout()

        processAndUploadCalls(reportBody.Body.Rows)

    }

	protected Map<Integer, Set<EmployeeKey>> buildEmployeeKeyMap() {        
		return redshiftReader.buildEmployeeKeyMap() as Map<Integer, Set<EmployeeKey>> 
    }

    protected Map<Integer, Set<StoreKey>> buildStoreKeyMap() {
		return redshiftReader.buildStoreKeyMap() as Map<Integer, Set<StoreKey>> 
    }

    private void processAndUploadCalls(def calls) {
        final ExecutorService executor = Executors.newFixedThreadPool(12)

        int count = 0
        final List<PhoneCall> buffer = new ArrayList<>()
        calls.each { final phoneCallRaw ->
            if (batchIsFull(count)) {
                LOGGER.info("$count records have been extracted and translated")

                // dump the buffer content into a new collection
                List<PhoneCall> recordList = new ArrayList<>()
                buffer.each { recordList.add(it) }

                // clear the buffer
                buffer.clear()

                // write the records to a S3 object
                executor.execute {
                    createS3Object(recordList)
                }
                count = 0
            }

            count += processPhoneCall(phoneCallRaw, buffer)

            if (buffer.size() > 0) {
                // if the buffer is not empty, write its content to a S3 object
                executor.execute {
                    createS3Object(buffer)
                }
            }

            executor.shutdown()

            LOGGER.info("$count records in total were extracted.")
        }
    }

    public Integer processPhoneCall(def phoneCallRaw, List<PhoneCall> buffer) {

        def rawStats = phoneCallRaw.Statistics
        PhoneCall phoneCall = new PhoneCall()

        rawStats.each {
            switch (it.ID) {
                case 3586:
                    phoneCall.m_status = it.Value
                    break
                case 3556:
                    phoneCall.m_timeStartedDistribution = it.Value
                    break
                case 3572:
                    phoneCall.m_callRingTimeDistribution = it.Value
                    break
                case 3573:
                    phoneCall.m_callTalkTime = it.Value
                    break
                case 3553:
                    phoneCall.m_segmentNumber = it.Value
                    break
                case 3561:
                    phoneCall.m_deviceIdCalling = it.Value
                    break
                case 3565:
                    phoneCall.m_telephoneNumberCalling = it.Value
                    break
                case 3562:
                    phoneCall.m_deviceIdCalled = it.Value
                    break
                case 3566:
                    phoneCall.m_telephoneNumberCalled = it.Value
                    break
                case 3563:
                    phoneCall.m_deviceIdOffered = it.Value
                    break
                case 3578:
                    phoneCall.m_deviceNameOffered = it.Value
                    break
                case 3584:
                    phoneCall.m_answered = it.Value
                    break
                case 3552:
                    phoneCall.m_callType = it.Value
                    break
                case 3564:
                    phoneCall.m_deviceIdMovedFrom = it.Value
                    break
                case 3567:
                    phoneCall.m_telephoneNumberMovedTo = it.Value
                    break
                case 3577:
                    phoneCall.m_deviceNameCalled = it.Value
                    break
                case 3503:
                    phoneCall.m_partitionName = it.Value
                    break
                case 3595:
                    phoneCall.m_telephoneNumberCalledDescription = it.Value
                    break
                case 3594:
                    phoneCall.m_telephoneNumberCallingDescription = it.Value
                    break
                case 3558:
                    phoneCall.m_timeAnsweredAt = it.Value
                    break
                case 3559:
                    phoneCall.m_timeEndedAt = it.Value
                    break
                case 3574:
                    phoneCall.m_callTime = it.Value
                    break
                case 3575:
                    phoneCall.m_calHeldTime = it.Value
                    break
                case 3569:
                    phoneCall.m_agentIdCalled = it.Value
                    break
                case 3581:
                    phoneCall.m_agentNameCalled = it.Value
                    break
                case 3583:
                    phoneCall.m_agentNameMovedFrom = it.Value
                    break
                case 3588:
                    phoneCall.m_didDigits = it.Value
                    break
                case 3592:
                    phoneCall.m_dnisDescription = it.Value
                    break
                case 3501:
                    phoneCall.m_systemName = it.Value
                    break
                case 3596:
                    phoneCall.m_telephoneNumberMovedToDescription = it.Value
                    break
                case 3576:
                    phoneCall.m_deviceNameCalling = it.Value
                    break
                case 3579:
                    phoneCall.m_deviceNameMovedFrom = it.Value
                    break
                case 3631:
                    phoneCall.m_extensionCalled = it.Value
                    break
                case 3630:
                    phoneCall.m_extensionCalling = it.Value
                    break
                case 3633:
                    phoneCall.m_extensionMovedFrom = it.Value
                    break
                case 3632:
                    phoneCall.m_extensionOffered = it.Value
                    break
                case 3635:
                    phoneCall.m_agentExtensionCalled = it.Value
                    break
                case 3634:
                    phoneCall.m_agentExtensionCalling = it.Value
                    break
                case 3637:
                    phoneCall.m_agentExtensionMovedFrom = it.Value
                    break
                case 3636:
                    phoneCall.m_agentExtensionOffered = it.Value
                    break
                case 3568:
                    phoneCall.m_agentIdCalling = it.Value
                    break
                case 3571:
                    phoneCall.m_agentIdMovedFrom = it.Value
                    break
                case 3570:
                    phoneCall.m_agentIdOffered = it.Value
                    break
                case 3580:
                    phoneCall.m_agentNameCalling = it.Value
                    break
                case 3582:
                    phoneCall.m_agentNameOffered = it.Value
                    break
                case 3555:
                    phoneCall.m_reasonEnded = it.Value
                    break
                case 3554:
                    phoneCall.m_reasonStarted = it.Value
                    break
                default:
                    LOGGER.warn("WARNING: field is unknown id number: ${it}")
            }  

        }


        // TODO: Add phone call to buffer?
        buffer.add(phoneCall)

        return 1
    }

    private Map runReport(String reportId) {
        String reportUrlStr = "${akixiApiUrl}/report/${reportId}/exec"
        LOGGER.info("Runn report from Axiki API at ${reportUrlStr}")

        def reportUrl = new URL(reportUrlStr)
        HttpURLConnection reportConnection = (HttpURLConnection) reportUrl.openConnection()
        reportConnection.setRequestMethod("GET")
		reportConnection.setRequestProperty("Cookie", "JSESSIONID=${sessionId};")
        reportConnection.setRequestProperty("Accept", "*/*")
        
		Integer reportResponseCode = reportConnection.getResponseCode()

        List<ReportHeader> reports = new ArrayList<ReportHeader>()

        String reportStatus = ""

        def reportBody = null

		while ((reportIsWaiting(reportStatus) || reportStatus == "") && responseCodeIsSuccessful(reportResponseCode)) {
            LOGGER.info("Successfully got reports from Axiki API with response code ${reportResponseCode}")
            BufferedReader br = new BufferedReader(new InputStreamReader(reportConnection.getInputStream()));
            
			StringBuilder sb = new StringBuilder();
			String strCurrentLine;
			while ((strCurrentLine = br.readLine()) != null) {
    			sb.append(strCurrentLine+"\n");
			}
			br.close();
			String responseBody = sb.toString();
            LOGGER.info("Response body: ${responseBody}")
			def reportData = new JsonSlurper().parseText(responseBody)

            reportStatus = reportData.ExecutionStatus

            if (reportSuccessful(reportStatus)) {
                LOGGER.info("Report successful with status ${reportStatus}")
                reportBody = reportData
            } else {
                reportConnection = (HttpURLConnection) reportUrl.openConnection()
                reportConnection.setRequestMethod("GET")
                reportConnection.setRequestProperty("Cookie", "JSESSIONID=${sessionId};")
                reportConnection.setRequestProperty("Accept", "*/*")

                LOGGER.info("Report waiting or failed with status ${reportStatus}")
                reportResponseCode = reportConnection.getResponseCode()
            }
        }
        
        if (!responseCodeIsSuccessful(reportResponseCode)) {
            BufferedReader br = new BufferedReader(new InputStreamReader(reportConnection.getErrorStream()));
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                LOGGER.error(strCurrentLine);
            }
            System.exit(1)
        }

        return reportBody
    }

    private Boolean reportFailed(String reportStatus) {
        return ['CLOSED', 'CLOSING', 'UNLICENSED', 'ERROR'].contains(reportStatus)
    }

    private Boolean reportIsWaiting(String reportStatus) {
        return ['WAITING', 'INITIALISING'].contains(reportStatus)
    }
    
    private Boolean reportSuccessful(String reportStatus) {
        return ['ACTIVE'].contains(reportStatus)
    }

    private Boolean responseCodeIsSuccessful(Integer responseCode) {
        return 200 <= responseCode && responseCode <= 399
    }

    private List<ReportHeader> getReports() {
        String reportUrlStr = "${akixiApiUrl}/report"
        LOGGER.info("Getting reports from Axiki API at ${reportUrlStr}")

        def reportUrl = new URL(reportUrlStr)
        HttpURLConnection reportConnection = (HttpURLConnection) reportUrl.openConnection()
        reportConnection.setRequestMethod("GET")
		reportConnection.setRequestProperty("Cookie", "JSESSIONID=${sessionId};")
        reportConnection.setRequestProperty("Accept", "*/*")
        
		Integer reportResponseCode = reportConnection.getResponseCode()

        List<ReportHeader> reports = new ArrayList<ReportHeader>()

		if (200 <= reportResponseCode && reportResponseCode <= 399) {
            LOGGER.info("Successfully got reports from Axiki API with response code ${reportResponseCode}")
            BufferedReader br = new BufferedReader(new InputStreamReader(reportConnection.getInputStream()));
            
			StringBuilder sb = new StringBuilder();
			String strCurrentLine;
			while ((strCurrentLine = br.readLine()) != null) {
    			sb.append(strCurrentLine+"\n");
			}
			br.close();
			String responseBody = sb.toString();
            LOGGER.info("Response body: ${responseBody}")
			def reportArray = new JsonSlurper().parseText(responseBody)

            reportArray.each {
                LOGGER.info("Report ID: ${it.ID}")
                LOGGER.info("Report description: ${it.Description}")

                ReportHeader header = new ReportHeader(it.ID, it.Type, it.Description, it.IsLicensed)
                reports.add(header)
            }

        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(loginConnection.getErrorStream()));
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                LOGGER.error(strCurrentLine);
            }
            System.exit(1)
        }

        return reports
    }

    private void logout() {
        String logoutUrlStr = "${akixiApiUrl}/logout"
        LOGGER.info("Logging out of Axiki API at ${logoutUrlStr}")

        def loginUrl = new URL(logoutUrlStr)
        HttpURLConnection loginConnection = (HttpURLConnection) loginUrl.openConnection()
        loginConnection.setRequestMethod("GET")
		loginConnection.setRequestProperty("Cookie", "JSESSIONID=${sessionId};")
        loginConnection.setRequestProperty("Accept", "*/*")
        
		Integer loginResponseCode = loginConnection.getResponseCode()

		if (200 <= loginResponseCode && loginResponseCode <= 399) {
            LOGGER.info("Successfully logged out of Axiki API with response code ${loginResponseCode}")

        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(loginConnection.getErrorStream()));
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                LOGGER.error(strCurrentLine);
            }
        }
    }

    private String login(String username, String password) {
        String sessionCreationUrl = "${akixiApiUrl}/session"
        LOGGER.info("Creating Axiki API session at ${sessionCreationUrl}")

        def sessionUrl = new URL(sessionCreationUrl)
        HttpURLConnection sessionConnection = (HttpURLConnection) sessionUrl.openConnection()
        sessionConnection.setRequestMethod("POST")
		
		Integer sessionResponseCode = sessionConnection.getResponseCode()

        String sessionId = ""
		if (200 <= sessionResponseCode && sessionResponseCode <= 399) {
            BufferedReader br = new BufferedReader(new InputStreamReader(sessionConnection.getInputStream()));
            
            StringBuilder sb = new StringBuilder();
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                sb.append(strCurrentLine+"\n");
            }
            br.close();
            String responseBody = sb.toString();
            def responseJson = new JsonSlurper().parseText(responseBody)
            sessionId = responseJson.SessionID
            LOGGER.info("Successfully created session for Axiki API with session ID ${sessionId} and response code ${sessionResponseCode}")

        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(sessionConnection.getErrorStream()));
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                LOGGER.error(strCurrentLine);
            }
        }

        String loginUrlStr = "${akixiApiUrl}/login"
        LOGGER.info("Logging into Axiki API at ${loginUrlStr}")

        String auth = (akixiUsername + ":" + akixiPassword).bytes.encodeBase64().toString()
        def loginUrl = new URL(loginUrlStr)
        HttpURLConnection loginConnection = (HttpURLConnection) loginUrl.openConnection()
        loginConnection.setRequestMethod("GET")
		loginConnection.setRequestProperty("Authorization", "Basic ${auth}")
		loginConnection.setRequestProperty("Cookie", "JSESSIONID=${sessionId};")
        loginConnection.setRequestProperty("Accept", "*/*")
        
		Integer loginResponseCode = loginConnection.getResponseCode()

		if (200 <= loginResponseCode && loginResponseCode <= 399) {
            LOGGER.info("Successfully logged into Axiki API with response code ${loginResponseCode}")

        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(loginConnection.getErrorStream()));
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                LOGGER.error(strCurrentLine);
            }
        }

        return sessionId
    }

    
    protected boolean batchIsFull(final int count) {
        return ((count > 0) && (count >= m_batchSize))
    }
}