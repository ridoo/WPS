package org.n52.wps.server.algorithm.publishgrid;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.ggf.schemas.jsdl.x2005.x11.jsdl.ApplicationType;
import org.ggf.schemas.jsdl.x2005.x11.jsdl.DataStagingType;
import org.ggf.schemas.jsdl.x2005.x11.jsdl.JobDefinitionDocument;
import org.ggf.schemas.jsdl.x2005.x11.jsdl.JobDescriptionType;
import org.ggf.schemas.jsdl.x2005.x11.jsdl.JobIdentificationType;
import org.ggf.schemas.jsdl.x2005.x11.jsdl.impl.CreationFlagEnumerationImpl;
import org.ggf.schemas.jsdl.x2005.x11.jsdlPosix.FileNameType;
import org.ggf.schemas.jsdl.x2005.x11.jsdlPosix.POSIXApplicationDocument;
import org.ggf.schemas.jsdl.x2005.x11.jsdlPosix.POSIXApplicationType;
import org.n52.wps.PropertyDocument.Property;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.io.data.IData;
import org.n52.wps.io.data.binding.complex.URLListDataBinding;
import org.n52.wps.io.data.binding.literal.LiteralStringBinding;
import org.n52.wps.server.AbstractSelfDescribingAlgorithm;
import org.n52.wps.server.profiles.JavaSaga.SagaCallbackManager;

import org.ogf.saga.context.Context;
import org.ogf.saga.context.ContextFactory;
import org.ogf.saga.error.BadParameterException;
import org.ogf.saga.error.NoSuccessException;
import org.ogf.saga.job.Job;
import org.ogf.saga.monitoring.Metric;
import org.ogf.saga.monitoring.Monitorable;
import org.ogf.saga.session.Session;
import org.ogf.saga.session.SessionFactory;
import org.ogf.saga.url.URLFactory;
import xint.esa.ssegrid.wps.javaSAGAProfile.URLListDocument;
import xint.esa.ssegrid.wps.javaSAGAProfile.URLListDocument.URLList;

import com.terradue.ogf.saga.impl.job.JobDescription;
import com.terradue.ogf.saga.impl.job.JobFactory;
import com.terradue.ogf.saga.impl.job.JobImpl;
import com.terradue.ogf.saga.impl.job.JobServiceImpl;
import com.terradue.ogf.schema.jsdl.JSDLFactory;
import com.terradue.ssegrid.sagaext.JobServiceAssistant;
import com.terradue.ssegrid.sagaext.MyProxyClient;
import com.terradue.ssegrid.sagaext.ProcessingRegistry;

public class PublishData extends AbstractSelfDescribingAlgorithm {

	private static Logger LOGGER = Logger.getLogger(PublishData.class);

	private static String GridFilesDir;
	private static String SagaLibDir;
	private static org.ogf.saga.url.URL GridmapGLUE;
	private ProcessingRegistry processingRegistry = null;
	private String WPSPublicationPrefix;
	private String DeployProcessDir;
	private String myProxyURL;
	private String myProxyUser;
	private String myProxyPassword;


	public List<String> getInputIdentifiers() {
		List<String> list = new ArrayList<String>();
		list.add("sourceList");
		list.add("rootDir");
		return list;
	}

	public Class getInputDataType(String identifier) {
		if (identifier.equalsIgnoreCase("sourceList")) {
			return URLListDataBinding.class;
		}
		if (identifier.equalsIgnoreCase("rootDir")) {
			return LiteralStringBinding.class;
		}
		return null;
	}

	public List<String> getOutputIdentifiers() {
		List<String> list = new ArrayList<String>();
		list.add("publishedList");
		return list;
	}

	public Class getOutputDataType(String identifier) {
		if (identifier.equalsIgnoreCase("publishedList")) {
			return URLListDataBinding.class;
		}
		return null;
	}

	public Map<String, IData> run(Map<String, List<IData>> inputData) {
		// create unique process instance id

		HashMap<String, IData> result = new HashMap<String, IData>();
		try {
			String pii = UUID.randomUUID().toString();
			// get JavaSaga Repository properties
			getSagaRepositoryProperties();
			// wps MAP
			// Initialize WPS Map
			String processID = "PublishData";
			HashMap<String, String> WPSmap = new HashMap<String, String>();
			WPSmap.put("WPS_DEPLOY_PROCESS_DIR", GridFilesDir
					+ "deploy/process/");
			WPSmap.put("WPS_DEPLOY_AUXDATA_DIR", GridFilesDir
					+ "deploy/auxdata/");
			WPSmap.put("WPS_JOB_INPUTS_DIR", GridFilesDir + "execute/"
					+ processID + "/" + pii + "/${GAI_JOB_UID}/inputs");
			WPSmap.put("WPS_JOB_OUTPUTS_DIR", GridFilesDir + "execute/"
					+ processID + "/" + pii + "/${GAI_JOB_UID}/outputs");
			WPSmap.put("WPS_JOB_AUDITS_DIR", GridFilesDir + "execute/"
					+ processID + "/" + pii + "/${GAI_JOB_UID}/audits");
			WPSmap.put("WPS_JOB_RESULTS_DIR", GridFilesDir + "execute/"
					+ processID + "/" + pii + "/${GAI_JOB_UID}/results");
			WPSmap.put("WPS_JOB_RESULTS_URL", WPSPublicationPrefix + "execute/"+ processID
					+ "/" + pii + "/${GAI_JOB_UID}/results");
			LOGGER.info("pii:" + pii);
			// load registry
			processingRegistry = new ProcessingRegistry(false);
			// Read inputs
			List<IData> sourceListData = inputData.get("sourceList");
			if (sourceListData == null || sourceListData.size() != 1) {
				throw new RuntimeException(
						"Error while allocating input parameters");
			}
			List<IData> rootDirData = inputData.get("rootDir");
			if (rootDirData == null || rootDirData.size() != 1) {
				throw new RuntimeException(
						"Error while allocating input parameters");
			}
			URLListDocument sourceListDoc = (URLListDocument) sourceListData
					.get(0).getPayload();
			String rootDir = (String) rootDirData
					.get(0).getPayload();
			if(!rootDir.endsWith(File.separator)) {
				rootDir = rootDir + File.separator;
			}
			// Retrieve this algorithm directory path
			String thisPath = this.getClass().getProtectionDomain()
					.getCodeSource().getLocation().toString();
			thisPath = thisPath.replaceFirst("file:", "");
			thisPath = thisPath.substring(0, thisPath.indexOf("WEB-INF"))
					+ "WEB-INF";
			String thisDir = thisPath + File.separator + "TempJSDL";
			LOGGER.info("thisDir: " + thisDir);
			(new File(thisDir)).mkdirs();
			String dummyDir = GridFilesDir + "deploy/process/Dummy/";
			String dummyPath = dummyDir + "dummy.sh";
			(new File(dummyDir)).mkdirs();
			LOGGER.info("dummyPath: " + dummyPath);
			(new File(dummyPath)).createNewFile();

			// Construct the JSDL

			JobDefinitionDocument jsdlDoc = JobDefinitionDocument.Factory
					.newInstance();
			JobDescriptionType jsdlDec = jsdlDoc.addNewJobDefinition()
					.addNewJobDescription();
			JobIdentificationType jobId = jsdlDec.addNewJobIdentification();
			jobId.setJobName("PublishData_${GAI_JOB_UID}");
			jobId.setDescription("Transfers a file (or a directory of files) from one Grid to another");
			jobId.addJobProject("SSEGrid");
			ApplicationType jobApp = jsdlDec.addNewApplication();
			POSIXApplicationDocument posix = POSIXApplicationDocument.Factory
					.newInstance();

			POSIXApplicationType posixApp = posix.addNewPOSIXApplication();
			FileNameType echo = FileNameType.Factory.newInstance();
			echo.setStringValue("dummy.sh");
			posixApp.setExecutable(echo);
			// FileNameType stdin = FileNameType.Factory.newInstance();
			// stdin.setStringValue("stdin");
			// posixApp.setInput(stdin);
			FileNameType stdout = FileNameType.Factory.newInstance();
			stdout.setStringValue("stdout");
			posixApp.setOutput(stdout);
			FileNameType error = FileNameType.Factory.newInstance();
			error.setStringValue("stderr");
			posixApp.setError(error);
			org.ggf.schemas.jsdl.x2005.x11.jsdlPosix.DirectoryNameType working = org.ggf.schemas.jsdl.x2005.x11.jsdlPosix.DirectoryNameType.Factory
					.newInstance();
			working.setStringValue("${GAI_JOB_WORKING_DIR}");
			//posixApp.setWorkingDirectory(working);
			jobApp.set(posix);
			jobApp.setApplicationName("PublishData");
			DataStagingType dummyStage = jsdlDec.addNewDataStaging();
			dummyStage.setFileName("${GAI_JOB_WORKING_DIR}/dummy.sh");
			dummyStage.setCreationFlag(CreationFlagEnumerationImpl.OVERWRITE);
			dummyStage.addNewSource().setURI(
					"${WPS_DEPLOY_PROCESS_DIR}Dummy/dummy.sh");

			DataStagingType exitStage = jsdlDec.addNewDataStaging();
			exitStage.setFileName("${GAI_JOB_WORKING_DIR}/exitMessage");
			exitStage.setCreationFlag(CreationFlagEnumerationImpl.OVERWRITE);
			exitStage.addNewTarget().setURI(
					"${WPS_JOB_OUTPUTS_DIR}/exitMessage");

			DataStagingType outStage = jsdlDec.addNewDataStaging();
			outStage.setFileName("${GAI_JOB_WORKING_DIR}/stdout");
			outStage.setCreationFlag(CreationFlagEnumerationImpl.OVERWRITE);
			outStage.addNewTarget().setURI("${WPS_JOB_OUTPUTS_DIR}/stdout");

			DataStagingType errStage = jsdlDec.addNewDataStaging();
			errStage.setFileName("${GAI_JOB_WORKING_DIR}/stderr");
			errStage.setCreationFlag(CreationFlagEnumerationImpl.OVERWRITE);
			errStage.addNewTarget().setURI("${WPS_JOB_OUTPUTS_DIR}/stderr");

			// Modify jsdlDoc
			for (int i = 0; i < sourceListDoc.getURLList().getUrlArray().length; i++) {
				String sourceURL = sourceListDoc.getURLList().getUrlArray(i);
				String subURL = sourceURL.substring(rootDir.length());
				MessageDigest subHash = MessageDigest.getInstance("SHA");
				String hashFile = java.net.URLEncoder.encode("TrFile"+subHash.digest(subURL.getBytes()).toString()+".tmp", "UTF-8");
				String destinationURL = "${WPS_JOB_RESULTS_DIR}/"+subURL;
				DataStagingType staging = jsdlDoc.getJobDefinition()
						.getJobDescription().addNewDataStaging();
				staging.setFileName(hashFile);
				staging.setCreationFlag(CreationFlagEnumerationImpl.OVERWRITE);
				staging.setDeleteOnTermination(true);
				staging.addNewSource().setURI(sourceURL);
				DataStagingType staging2 = jsdlDoc.getJobDefinition()
				.getJobDescription().addNewDataStaging();
		staging2.setFileName(hashFile);
		staging2.setCreationFlag(CreationFlagEnumerationImpl.OVERWRITE);
		staging2.setDeleteOnTermination(true);
		staging2.addNewTarget().setURI(destinationURL);
			}
			LOGGER.info("jsdl: " + jsdlDoc.toString());
			File jsdlModifiedFile = new File(thisDir + File.separator + "jsdl_"
					+ pii + ".xml");
			jsdlDoc.save(jsdlModifiedFile);
			LOGGER.info("written modified file: " + jsdlModifiedFile.getPath());

			Session session = SessionFactory.createSession(false);
			Context context = ContextFactory.createContext("globus");
			context.setAttribute(Context.USERPROXY, GridFilesDir + "proxy");
			session.addContext(context);
			// Get delegation to that user proxy and set propoerly context
			MyProxyClient.delegateProxyFromMyProxyServer(myProxyURL, 7512,
					myProxyUser, myProxyPassword, 604800, context);
			JobServiceImpl js = JobFactory.createJobService(session/*
																	 * ,
																	 * gridmapGLUE
																	 */);
			JobServiceAssistant jsa = new JobServiceAssistant(js);
			jsa.addSubstitutionVariables(WPSmap);
			JobDescription jd = (JobDescription) JobFactory
					.createJobDescription(JSDLFactory
							.createJSDLDocument(jsdlModifiedFile));
			// jsa.substituteSimpleInputs(jd, (Map<String,String>)(new
			// HashMap<String,String>()));
			JobImpl jobs = null;
			jobs = (JobImpl) ((JobServiceImpl) js).createJob(jd);
			// create now the job execute dirs
			String inputsDir = jobs
					.getSubstitutedVariable("WPS_JOB_INPUTS_DIR");
			String outputsDir = jobs
					.getSubstitutedVariable("WPS_JOB_OUTPUTS_DIR");
			String auditsDir = jobs
					.getSubstitutedVariable("WPS_JOB_AUDITS_DIR");
			String resultsDir = jobs
					.getSubstitutedVariable("WPS_JOB_RESULTS_DIR");
			String publishedDir = jobs
			.getSubstitutedVariable("WPS_JOB_RESULTS_URL");
			(new File(inputsDir)).mkdirs();
			(new File(outputsDir)).mkdirs();
			(new File(auditsDir)).mkdirs();
			(new File(resultsDir)).mkdirs();
			(new File(resultsDir)).setWritable(true, false);
			// Create all destination directories and create output URL list
			URLListDocument publishedDataDoc = URLListDocument.Factory.newInstance();
			 URLList urlList = publishedDataDoc.addNewURLList();
			 urlList.setCount(sourceListDoc.getURLList().getCount());
			for (int i = 0; i < sourceListDoc.getURLList().getUrlArray().length; i++) {
				String sourceURL = sourceListDoc.getURLList().getUrlArray(i);
				String subURL = sourceURL.substring(rootDir.length());
				if(subURL.startsWith(File.separator)) {
					subURL = subURL.substring(1);
				}
				String subURLDir = subURL.substring(0, subURL.lastIndexOf(File.separator)+1); 
				String destinationURL = resultsDir+File.separator+subURLDir;
				File destinationFile= new File(destinationURL);
				destinationFile.mkdirs();
				destinationFile.setWritable(true,false);
				urlList.addUrl(publishedDir+ File.separator+subURL);
			}
			jobs.addCallback(Job.JOB_STATE, new SagaCallbackManager());
			jobs.addCallback(Job.JOB_STATEDETAIL, new SagaCallbackManager());
			jobs.run();
			jobs.waitFor();
			boolean exitFault = false;
			String[][] exitMessages;
			exitMessages = jsa.readExitMessages(jobs);
			for (String[] exitMessage : exitMessages) {
				if (!exitMessage[0].trim().equals("0")) {
					exitFault = true;
					break;
				}
			}
			result.put("publishedList", new URLListDataBinding(publishedDataDoc));
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.info("Exception: " + e.getMessage());
			throw new RuntimeException(e);
		}
		return result;

	}

	private void getSagaRepositoryProperties() throws BadParameterException,
			NoSuccessException {
		Property[] properties = WPSConfig.getInstance()
				.getPropertiesForRepositoryName("JavaSagaRepository");
		Property WPSSaga = WPSConfig.getInstance().getPropertyForKey(
				properties, "GridFilesDir");
		if (WPSSaga == null) {
			throw new RuntimeException(
					"Error. Could not find the required GridFilesDir property in wps_config.xml");
		}
		GridFilesDir = WPSSaga.getStringValue();
		Property sagaLibProp = WPSConfig.getInstance().getPropertyForKey(
				properties, "SagaLibDir");
		if (sagaLibProp == null) {
			throw new RuntimeException(
					"Error. Could not find the required SagaLibDir property in wps_config.xml");
		}
		SagaLibDir = (sagaLibProp.getStringValue());
		Property wpsPublicRoot = WPSConfig.getInstance().getPropertyForKey(
				properties, "WPSPublicationPrefix");
		if (wpsPublicRoot == null) {
			throw new RuntimeException(
					"Error. Could not find WPSPublicationPrefix");
		}
		WPSPublicationPrefix = wpsPublicRoot.getStringValue();

		// Set the deployement process directory
		DeployProcessDir = (GridFilesDir + "deploy/process/");
		Property gridmap = WPSConfig.getInstance().getPropertyForKey(
				properties, "GridGlue");
		if (gridmap == null) {
			throw new RuntimeException(
					"Error. Could not find the required GridGlue property in wps_config.xml");
		}
		// Saga.location must be loaded before the following line
		GridmapGLUE = URLFactory.createURL(gridmap.getStringValue());
		// Note system properties must already be set by a previous run
		Property myProxyURLProp = WPSConfig.getInstance().getPropertyForKey(
				properties, "MyProxyURL");
		if (myProxyURLProp == null) {
			throw new RuntimeException(
					"Error. Could not find the required MyProxyUser property in wps_config.xml");
		}
		myProxyURL = myProxyURLProp.getStringValue();

		Property myProxyUserProp = WPSConfig.getInstance().getPropertyForKey(
				properties, "MyProxyUser");
		if (myProxyUserProp == null) {
			throw new RuntimeException(
					"Error. Could not find the required MyProxyUser property in wps_config.xml");
		}
		myProxyUser = myProxyUserProp.getStringValue();

		Property myProxyPasswordProp = WPSConfig.getInstance()
				.getPropertyForKey(properties, "MyProxyPassword");
		if (myProxyPasswordProp == null) {
			throw new RuntimeException(
					"Error. Could not find the required MyProxyUser property in wps_config.xml");
		}
		myProxyPassword = myProxyPasswordProp.getStringValue();

	}

	// Callback monitors job.
	public boolean cb(Monitorable m, Metric metric, Context ctxt) {
		try {
			String value = metric.getAttribute(Metric.VALUE);
			String name = metric.getAttribute(Metric.NAME);
			System.out.println("Callback called for metric " + name
					+ ", value = " + value);
		} catch (Throwable e) {
			System.err.println("error" + e);
			e.printStackTrace(System.err);
		}
		// Keep the callback.
		return true;
	}

	public void setMyProxyURL(String myProxyURL) {
		this.myProxyURL = myProxyURL;
	}

	public String getMyProxyURL() {
		return myProxyURL;
	}

	public void setMyProxyUser(String myProxyUser) {
		this.myProxyUser = myProxyUser;
	}

	public String getMyProxyUser() {
		return myProxyUser;
	}

	public void setMyProxyPassword(String myProxyPassword) {
		this.myProxyPassword = myProxyPassword;
	}

	public String getMyProxyPassword() {
		return myProxyPassword;
	}
}