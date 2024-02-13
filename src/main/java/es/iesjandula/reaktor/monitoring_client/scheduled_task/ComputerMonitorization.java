package es.iesjandula.reaktor.monitoring_client.scheduled_task;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.iesjandula.reaktor.exceptions.ComputerError;
import es.iesjandula.reaktor.models.Action;
import es.iesjandula.reaktor.models.CommandLine;
import es.iesjandula.reaktor.models.Computer;
import es.iesjandula.reaktor.models.HardwareComponent;
import es.iesjandula.reaktor.models.Location;
import es.iesjandula.reaktor.models.MonitorizationLog;
import es.iesjandula.reaktor.models.Software;
import es.iesjandula.reaktor.models.Status;
import es.iesjandula.reaktor.models.Task;
import es.iesjandula.reaktor.models.monitoring.Actions;
import es.iesjandula.reaktor.monitoring_client.utils.exceptions.ReaktorClientException;
import es.iesjandula.reaktor.monitoring_server.repository.ITaskRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * @author David Martinez
 *
 */
@Slf4j
@Component
public class ComputerMonitorization
{
	/**
	 * Method sendFullComputerTask scheduled task
	 * 
	 * @throws ReaktorClientException
	 */
	@Scheduled(fixedDelayString = "5000", initialDelay = 2000)
	public void sendFullComputerTask() throws ReaktorClientException
	{
		// THE COMPUTER FAKE FULL INFO STATUS
		Computer computerInfoMob = new Computer("sn1234", "and123", "cn123", "windows", "paco",
				new Location("0.5", 0, "trolley1"), new ArrayList<HardwareComponent>(),
				new ArrayList<Software>(List.of(new Software("Virtual Box"), new Software("PokeGame"))),
				new CommandLine(), new MonitorizationLog());

		// Object mapper
		ObjectMapper mapper = new ObjectMapper();

		// --- CLOSEABLE HTTP ---
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;

		try
		{
			// --- GETTING THE COMPUTER AS STRING ---
			String computerString = mapper.writeValueAsString(computerInfoMob);
			// GETTING COMPUTER AS STRING ENTITY
			StringEntity computerStringEntity = new StringEntity(computerString);

			// GETTING HTTP CLIENT
			httpClient = HttpClients.createDefault();

			// DO THE HTTP POST WITH PARAMETERS
			HttpPost request = new HttpPost("http://localhost:8084/computers/send/fullInfo");
			request.setHeader("Content-Type", "application/json");
			request.setHeader("serialNumber", "sn1234");
			request.setEntity(computerStringEntity);

			response = httpClient.execute(request);

			String responseString = EntityUtils.toString(response.getEntity());
			log.info(responseString);
		}
		catch (JsonProcessingException exception)
		{
			String error = "Error Json Processing Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		catch (UnsupportedEncodingException exception)
		{
			String error = "Error Unsupported Encoding Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		catch (ClientProtocolException exception)
		{
			String error = "Error Client Protocol Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		catch (IOException exception)
		{
			String error = "Error In Out Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		finally
		{
			closeHttpClientResponse(httpClient, response);
		}
	}

	/**
	 * Method sendStatusComputerTask scheduled task
	 * 
	 * @throws ReaktorClientException
	 */
	@Scheduled(fixedDelayString = "6000", initialDelay = 2000)
	public void sendStatusComputerTask() throws ReaktorClientException
	{
		
		String serialNumber = "sn123556";
		Status status = new Status();
		// --- CLOSEABLE HTTP ---
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;

		try
		{
			// GETTING HTTP CLIENT
			httpClient = HttpClients.createDefault();

			// DO THE HTTP GET WITH PARAMETERS
			HttpGet request = new HttpGet("http://localhost:8084/computers/get/pendingActions");
			request.setHeader("serialNumber", serialNumber);

			response = httpClient.execute(request);

			String responseString = EntityUtils.toString(response.getEntity());
			log.info(responseString);

			Task actionsToDo = new ObjectMapper().readValue(responseString, Task.class);
			
			try
			{
				String command;
				if(System.getProperty("os.name").toLowerCase().contains("windows"))
				{
					command = actionsToDo.getAction().getCommandWindows();
				}
				else
				{
					command = actionsToDo.getAction().getCommandLinux();
				}
				
				switch(actionsToDo.getAction().getName())
				{
					case "BlockUsbs":
					{
						
					}
					case "ConfigWifi":
					{
						
					}
					default:
					{
						log.info("Realizando el commando "+actionsToDo.getAction().getName());		
						actionsToDo.setStatus(Action.STATUS_DONE);
						sendStatus(serialNumber, actionsToDo, httpClient);
						this.executeCommand(command, actionsToDo.getInfo());
					}
				}
			}
			catch (ComputerError exception) 
			{
				String error = "Error realizando la accion " + actionsToDo.getTaskId().getActionName();
				log.error(error,exception);
				actionsToDo.setStatus(Action.STATUS_FAILURE);
			}
			
		}
		catch (JsonProcessingException exception)
		{
			String error = "Error Json Processing Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		catch (UnsupportedEncodingException exception)
		{
			String error = "Error Unsupported Encoding Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		catch (ClientProtocolException exception)
		{
			String error = "Error Client Protocol Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		catch (IOException exception)
		{
			String error = "Error In Out Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		finally
		{
			this.closeHttpClientResponse(httpClient, response);
		}

	}

	/**
	 * @param serialNumber
	 * @param status
	 * @param httpClient
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws UnsupportedEncodingException
	 * @throws JsonProcessingException
	 * @throws ClientProtocolException
	 */
	private void sendStatus(String serialNumber, Task status, CloseableHttpClient httpClient)
			throws FileNotFoundException, IOException, UnsupportedEncodingException, JsonProcessingException,
			ClientProtocolException
	{
		//---- UPDATE ANDALUCIA - S/N - COMPUTER NUMBER SNAKE YAML START -----
		// -- GETING THE MONITORIZATION YML , WITH THE INFO ---
		InputStream inputStream = new FileInputStream(new File("./src/main/resources/monitorization.yml"));
		Yaml yaml = new Yaml();
		
		// --- LOADING THE INFO INTO STRING OBJECT MAP ---
		Map<String, Object> yamlMap = yaml.load(inputStream);
		
		// --- GETTING THE INFO INTO STRING STRING MAP (CAST)---
		Map<String, String> computerMonitorizationYml = (Map<String, String>) yamlMap.get("ComputerMonitorization");

		// --- LOG THE INFO FROM THE MAP ---
		log.info("andaluciaId: " + computerMonitorizationYml.get("andaluciaId"));
		log.info("computerNumber: " + computerMonitorizationYml.get("computerNumber"));
		log.info("serialNumber: " + computerMonitorizationYml.get("serialNumber"));
		//---- UPDATE ANDALUCIA - S/N - COMPUTER NUMBER SNAKE YAML END -----
		
		
		// --- UPDATE ACTIONS ---
//			this.updateAndaluciaId(statusList, serialNumber, actionsToDo, computerMonitorizationYml);
//			this.updateComputerNumber(statusList, serialNumber, actionsToDo, computerMonitorizationYml);
//			this.updateSerialNumber(statusList, serialNumber, actionsToDo, computerMonitorizationYml);
//			
		// -- SAVING ALL MAP INFO INTO MONITORIZATION.YML ---
		this.savingMonitorizationYmlCfg(computerMonitorizationYml);

		// --- ENDPOINT TO SEND STATUS TO SERVER ---
		log.info(status.toString());

		// GETTING NEW HTTP CLIENT
		CloseableHttpClient httpClientStatus = HttpClients.createDefault();

		// DO THE HTTP POST WITH PARAMETERS
		HttpPost requestPost = new HttpPost("http://localhost:8084/computers/send/status");
		requestPost.setHeader("Content-type", "application/json");
		
		// -- SETTING THE STATUS LIST ON PARAMETERS FOR POST PETITION ---
		StringEntity statusEntity = new StringEntity(new ObjectMapper().writeValueAsString(status));
		requestPost.setEntity(statusEntity);
		requestPost.setHeader("serialNumber", serialNumber);
		
		CloseableHttpResponse responseStatus = httpClient.execute(requestPost);
	}

	
	/**
	 * Method actionsCfgWifiFile
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 */
	private void actionsCfgWifiFile(List<Status> statusList, String serialNumber, Actions actionsToDo)
	{
		// --- IF THE STRING FILE IS NOT NULL OR EMPTY/BLANK ---
		if (actionsToDo.getConfigurationWifi()!=null && !actionsToDo.getConfigurationWifi().isBlank() && !actionsToDo.getConfigurationWifi().isEmpty())
		{
			try
			{
				// --- IF THE FILE EXISTS AND IS A FILE ---
				File cfgFile = new File(actionsToDo.getConfigurationWifi());
				if(cfgFile.exists() && cfgFile.isFile()) 
				{
					// --- RUN COMMAND ---
					log.info(" ADD CFG WIFI -- > cmd.exe /c "+cfgFile.getAbsolutePath());
					Runtime.getRuntime().exec
					(
					"cmd.exe /c netsh wlan add profile filename="+cfgFile.getAbsolutePath()+""
					);
					
					// -- STATUD DONE --
					Status status = new Status("ADD CFG WIFI exec " + serialNumber, true, null);
					statusList.add(status);
				}
				else 
				{
					// --- ERROR ON FILE ---
					Status status = new Status("ADD CFG WIFI Error " + serialNumber, false,
							new ComputerError(666, "error CFG doesnt exist or is not a file", null));
					statusList.add(status);
				}	
			}
			catch (Exception exception)
			{
				// --- ERROR ON ACTION ---
				Status status = new Status("ADD CFG WIFI Error " + serialNumber, false,
						new ComputerError(666, "error on ADD CFG WIFI", null));
				statusList.add(status);
			}
		}
	}

	/**
	 * Method actionsInstallApps
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 */
	private void actionsInstallApps(List<Status> statusList, String serialNumber, Actions actionsToDo)
	{
		if (actionsToDo.getInstallApps() != null && !actionsToDo.getInstallApps().isEmpty())
		{
			try
			{
				for (String app : actionsToDo.getInstallApps())
				{
					log.info(" INSTALL -- > cmd.exe /c " + app);
					Runtime.getRuntime().exec
					(
					"cmd.exe /c winget install "+app+" --silent --accept-package-agreements --accept-source-agreements --force"
					);

				}
				Status status = new Status("Install App exec " + serialNumber, true, null);
				statusList.add(status);
			}
			catch (Exception exception)
			{
				Status status = new Status("Install app Error " + serialNumber, false,
						new ComputerError(666, "error on Install app", null));
				statusList.add(status);
			}
		}
	}
	
	
	/**
	 * Method actionsUninstallApps
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 */
	private void actionsUninstallApps(List<Status> statusList, String serialNumber, Actions actionsToDo)
	{
		if (actionsToDo.getUninstallApps() != null && !actionsToDo.getUninstallApps().isEmpty())
		{
			try
			{
				for (String app : actionsToDo.getUninstallApps())
				{
					log.info(" UNINSTALL -- > cmd.exe /c " + app);
					Runtime.getRuntime().exec
					(
					"cmd.exe /c winget uninstall --id "+app+" --silent --force"
					);

				}
				Status status = new Status("Uninstall App exec " + serialNumber, true, null);
				statusList.add(status);
			}
			catch (Exception exception)
			{
				Status status = new Status("Uninstall app Error " + serialNumber, false,
						new ComputerError(777, "error on Uninstall app", null));
				statusList.add(status);
			}
		}
	}

	/**
	 * Method closeHttpClientResponse
	 * @param httpClient
	 * @param response
	 * @throws ReaktorClientException
	 */
	private void closeHttpClientResponse(CloseableHttpClient httpClient, CloseableHttpResponse response)
			throws ReaktorClientException
	{
		if (httpClient != null)
		{
			try
			{
				httpClient.close();
			}
			catch (IOException exception)
			{
				String error = "Error In Out Exception";
				log.error(error, exception);
				throw new ReaktorClientException(exception);
			}
		}
		if (response != null)
		{
			try
			{
				response.close();
			}
			catch (IOException exception)
			{
				String error = "Error In Out Exception";
				log.error(error, exception);
				throw new ReaktorClientException(exception);
			}
		}
	}

	/**
	 * Method savingMonitorizationYmlCfg
	 * @param computerMonitorizationYml
	 * @throws IOException
	 */
	private void savingMonitorizationYmlCfg(Map<String, String> computerMonitorizationYml) throws IOException
	{
		if(computerMonitorizationYml!=null) 
		{
			// --- OPCION RAW , NUEVO YML CON LA INFO ---
			PrintWriter printWriter = new PrintWriter(new FileWriter("./src/main/resources/monitorization.yml"));
			printWriter.print(
						"ComputerMonitorization:\n"
					+ "  andaluciaId: \""+computerMonitorizationYml.get("andaluciaId")+"\"\n"
					+ "  computerNumber: \""+computerMonitorizationYml.get("computerNumber")+"\"\n"
					+ "  serialNumber: \"sn12345577\"");
			printWriter.flush();
			printWriter.close();
		}
	}

	/**
	 * Method updateSerialNumber
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 * @param computerMonitorizationYml
	 */
	private void updateSerialNumber(List<Status> statusList, String serialNumber, Actions actionsToDo,
			Map<String, String> computerMonitorizationYml)
	{
		if (actionsToDo.getUpdateSerialNumber() != null && !actionsToDo.getUpdateSerialNumber().isEmpty())
		{
			try
			{
				log.info("UPDATE SERIAL NUMBER TO - " + actionsToDo.getUpdateSerialNumber());
				if(computerMonitorizationYml!=null) 
				{
					computerMonitorizationYml.put("serialNumber", actionsToDo.getUpdateSerialNumber());
				}
				Status status = new Status("Update serialNumber" + serialNumber, true, null);
				statusList.add(status);
			}
			catch (Exception exception)
			{
				Status status = new Status("Update serialNumber " + serialNumber, false,
						new ComputerError(024, "error Update serialNumber ", null));
				statusList.add(status);
			}
		}
	}

	/**
	 * Method updateComputerNumber
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 * @param computerMonitorizationYml
	 */
	private void updateComputerNumber(List<Status> statusList, String serialNumber, Actions actionsToDo,
			Map<String, String> computerMonitorizationYml)
	{
		if (actionsToDo.getUpdateComputerNumber() != null && !actionsToDo.getUpdateComputerNumber().isEmpty())
		{
			try
			{
				log.info("UPDATE COMPUTER NUMBER TO - " + actionsToDo.getUpdateComputerNumber());
				if(computerMonitorizationYml!=null) 
				{
					computerMonitorizationYml.put("computerNumber", actionsToDo.getUpdateComputerNumber());
				}
				Status status = new Status("Update computerNumber" + serialNumber, true, null);
				statusList.add(status);
			}
			catch (Exception exception)
			{
				Status status = new Status("Update computerNumber " + serialNumber, false,
						new ComputerError(023, "error Update computerNumber ", null));
				statusList.add(status);
			}
		}
	}

	/**
	 * Method updateAndaluciaId
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 * @param computerMonitorizationYml
	 */
	private void updateAndaluciaId(List<Status> statusList, String serialNumber, Actions actionsToDo,
			Map<String, String> computerMonitorizationYml)
	{
		if (actionsToDo.getUpdateAndaluciaId() != null && !actionsToDo.getUpdateAndaluciaId().isEmpty())
		{
			try
			{
				log.info("UPDATE ANDALUCIA ID TO - " + actionsToDo.getUpdateAndaluciaId());
				if(computerMonitorizationYml!=null) 
				{
					computerMonitorizationYml.put("andaluciaId", actionsToDo.getUpdateAndaluciaId());
				}
				Status status = new Status("Update andaluciaId" + serialNumber, true, null);
				statusList.add(status);
			}
			catch (Exception exception)
			{
				Status status = new Status("Update andaluciaId " + serialNumber, false,
						new ComputerError(022, "error Update andaluciaId ", null));
				statusList.add(status);
			}
			
		}
	}
	
	/**
	 * this method make a screenshot and send it 
	 * @throws ReaktorClientException
	 */
	@Scheduled(fixedDelayString = "6000", initialDelay = 2000)
	public void getAndSendScreenshot() throws ReaktorClientException
	{
		String serialNumber = "sn123556";
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;

		httpClient = HttpClients.createDefault();

		HttpGet request = new HttpGet("http://localhost:8084/computers/get/screenshot");
		request.setHeader("serialNumber", serialNumber);

		try
		{
			response = httpClient.execute(request);
			String responseString = EntityUtils.toString(response.getEntity());
			log.info(responseString);

			if (responseString.equalsIgnoreCase("OK"))
			{
				try
				{
					BufferedImage image = new Robot()
							.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));

					ImageIO.write(image, "PNG", new File("./screen.png")); // your image will be saved at this path

				}
				catch (Exception exception)
				{
					String error = "Error making the screenshot";
					log.error(error, exception);
					throw new ReaktorClientException(exception);
				}
			}
		}
		catch (ClientProtocolException exception)
		{
			String error = "Client protocol error";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		catch (IOException exception)
		{
			String error = "Error In Out Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		finally
		{
			closeHttpClientResponse(httpClient, response);
		}
	 
	}
	
	/**
	 * this method actualice your computer info
	 * @throws ReaktorClientException
	 */
	@Scheduled(fixedDelayString = "6000", initialDelay = 2000)
	public void getAndChangeComputerInfo() throws ReaktorClientException
	{
		/*
		// fake computer info
		Computer thisComputerInfo = new Computer("sn123", "and123", "cn123", "windows", "paco", new Location("0.5", 0, "trolley1"),
				new ArrayList<>(), new ArrayList<>(), new CommandLine(),
				new MonitorizationLog());
		
		String serialNumber = "sn123";
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;

		httpClient = HttpClients.createDefault();

		HttpGet request = new HttpGet("http://localhost:8084/computers/get/status");
		request.setHeader("serialNumber", serialNumber);

		try
		{
			response = httpClient.execute(request);
			String responseString = EntityUtils.toString(response.getEntity());
			log.info("Objeto sel servidor:"+responseString);
			
			ObjectMapper objectMapper = new ObjectMapper();
			Computer serverComputer = objectMapper.readValue(responseString, Computer.class);
		
			if(thisComputerInfo.equals(serverComputer))
			{
				log.info("No computer status updates");
			}
			else
			{
				thisComputerInfo = serverComputer;
				log.info("The computer status was update");
			}
			
			
		}
		catch (ClientProtocolException exception)
		{
			String error = "Client protocol error";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		catch (IOException exception)
		{
			String error = "Error In Out Exception";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		finally
		{
			closeHttpClientResponse(httpClient, response);
		}
*/
	}

	

	/**
	 * Method actionsShutdown
	 * 
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 * @throws ReaktorClientException 
	 */
	private void executeCommand(String command, String info) throws ComputerError
	{	
		try
		{
			Runtime rt = Runtime.getRuntime();
			Process pr = rt.exec("cmd.exe /c "+ command + " " + info);
		}
		catch (Exception exception)
		{
			String error = "Error ejecutando el comando " + command;
			log.error(error,exception);
			throw new ComputerError(1,error,exception);
		}
		
	}
}
