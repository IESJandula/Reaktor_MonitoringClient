package es.iesjandula.reaktor.monitoring_client.scheduled_task;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;

import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.iesjandula.reaktor.exceptions.ComputerError;
import es.iesjandula.reaktor.models.Status;
import es.iesjandula.reaktor.models.DTO.TaskDTO;
import es.iesjandula.reaktor.monitoring_client.utils.HttpCommunicationSender;
import es.iesjandula.reaktor.monitoring_client.utils.exceptions.ReaktorClientException;
import lombok.extern.slf4j.Slf4j;
import es.iesjandula.reaktor.monitoring_client.models.Reaktor;

/**
 * @author David Martinez
 *
 */
@Slf4j
@Component
public class ComputerMonitorization
{

	/**
	 * - Attribute - this class is used to get the information of the Computer
	 */
	@Autowired
	private Reaktor reaktor;

	/**
	 * - Attribute - This class is used to manage communications with the server.
	 */
	@Autowired
	private HttpCommunicationSender httpCommunicationSender;

	/**
	 * - Attribute - This attrubte Storage the information about of Server URL
	 */
	@Value("${reaktor.server.url}")
	private String reaktorServerUrl;

	/**
	 * Method sendFullComputerTask scheduled task
	 * 
	 * @throws ReaktorClientException
	 */
	@Scheduled(fixedDelayString = "10000", initialDelay = 2000)
	public void sendFullComputerTask() throws ReaktorClientException
	{
		log.info("SENDING FULL INFO COMPUTER TO SERIALNUMBER -> "
				+ this.reaktor.getMotherboard().getComputerSerialNumber());
		this.httpCommunicationSender.sendPost(
				this.httpCommunicationSender.createHttpPostReaktor(this.reaktorServerUrl + "/reaktor", this.reaktor));
	}

	/**
	 * Method sendStatusComputerTask scheduled task
	 * 
	 * @throws ReaktorClientException
	 */
	@Scheduled(fixedDelayString = "6000", initialDelay = 2000)
	public void taskManager()
	{
		String serialNumber = this.reaktor.getMotherboard().getComputerSerialNumber();

		// --- CLOSEABLE HTTP ---
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;
		// GETTING HTTP CLIENT
		httpClient = HttpClients.createDefault();
		// DO THE HTTP GET WITH PARAMETERS
		HttpGet request = new HttpGet("http://localhost:8084/computers/get/pendingActions");
		request.setHeader("serialNumber", serialNumber);

		try
		{
			response = httpClient.execute(request);

			String responseString = EntityUtils.toString(response.getEntity());

			if (responseString != null && !responseString.isEmpty() && !responseString.isBlank())

			{

				try
				{
					TaskDTO task = new ObjectMapper().readValue(responseString, TaskDTO.class);
					String command = System.getProperty("os.name").toLowerCase().contains("windows")
							? task.getCommandWindows()
							: task.getCommandLinux();

					Status status = new Status();

					try
					{
						switch (task.getName())
						{
							case "updateAndaluciaId" -> this.updateAndaluciaId(task.getInfo());
							case "updateComputerNumber" -> this.updateComputerNumber(task.getInfo());
							case "updateSerialNumber" -> this.updateSerialNumber(task.getInfo());
							case "screenshot" -> this.getAndSendScreenshot(task);
							case "blockDisp" -> this.actionsBlockDisp(task.getInfo());
							case "configWifi" -> this.actionsCfgWifiFile(task.getInfo(), task, serialNumber);
							case "file" -> this.downloadFile("./", task, this.reaktor.getMotherboard().getComputerSerialNumber());
							case "command" -> this.executeCommand(task.getInfo(), task.getInfo());
							default -> this.executeCommand(command, task.getInfo());
						}
						status.setStatus(true);
						status.setError(null);
						status.setStatusInfo("task done succesfully");
					}
					catch (ComputerError computerError)
					{
						status.setStatus(false);
						status.setError(computerError);
						status.setStatusInfo("Error doing task " + task.getName());
					}
					catch (Exception exception)
					{
						status.setStatus(false);
						status.setError(new ComputerError(400, "Error doing task", exception));
						status.setStatusInfo("Error doing task " + task.getName());
					}
					status.setTaskDTO(task);

					// DO THE HTTP POST WITH PARAMETERS
					HttpPost requestPost = new HttpPost("http://localhost:8084/computers/send/status");
					requestPost.setHeader("Content-type", "application/json");

					// -- SETTING THE STATUS LIST ON PARAMETERS FOR POST PETITION ---
					StringEntity statusListEntity = new StringEntity(new ObjectMapper().writeValueAsString(status));
					requestPost.setEntity(statusListEntity);
					requestPost.setHeader("serialNumber", serialNumber);

					httpClient.execute(requestPost);
				}
				catch (IOException exception)
				{
					log.error(responseString);
				}
			}
		}
		catch (JsonProcessingException exception)
		{
			String error = "Error Json Processing Exception";
			log.error(error, exception);
		}
		catch (UnsupportedEncodingException exception)
		{
			String error = "Error Unsupported Encoding Exception";
			log.error(error, exception);
		}
		catch (ClientProtocolException exception)
		{
			String error = "Error Client Protocol Exception";
			log.error(error, exception);
		}
		catch (IOException exception)
		{
			String error = "Error In Out Exception";
			log.error(error, exception);
		}
		finally
		{
			this.closeHttpClientResponse(httpClient, response);
		}

	}

	/**
	 * Method that download a file
	 * 
	 * @param path
	 * @param taskDTO
	 * @param serialNumber
	 * @throws ComputerError
	 */
	private void downloadFile(String path, TaskDTO taskDTO, String serialNumber) throws ComputerError
	{
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;
		InputStream inputStream = null;
		try
		{
			// GETTING HTTP CLIENT
			httpClient = HttpClients.createDefault();

			// DO THE HTTP POST WITH PARAMETERS
			HttpPost requestPost = new HttpPost("http://localhost:8084/computers/get/file");
			requestPost.setHeader("Content-type", "application/json");

			// SET THE HEADER
			requestPost.setHeader("serialNumber", serialNumber);
			StringEntity taskDTOListEntity = new StringEntity(new ObjectMapper().writeValueAsString(taskDTO));
			requestPost.setEntity(taskDTOListEntity);
			response = httpClient.execute(requestPost);

			inputStream = response.getEntity().getContent();

			this.writeText(path + taskDTO.getInfo(), inputStream);
		}
		catch (IOException exception)
		{
			String error = "Error In Out Exception";
			log.error(error, exception);
			throw new ComputerError(1, error, exception);
		}
		finally
		{
			this.closeHttpClientResponse(httpClient, response);

			if (inputStream != null)
			{
				try
				{
					inputStream.close();
				}
				catch (IOException exception)
				{
					String error = "Error In Out Exception";
					log.error(error, exception);
					throw new ComputerError(1, error, exception);
				}
			}
		}
	}

	/**
	 * Method writeText
	 * 
	 * @param name
	 * @param content
	 */
	public void writeText(String name, InputStream input)
	{

		FileOutputStream fileOutputStream = null;

		DataOutputStream dataOutputStream = null;
		DataInputStream dataInputStream = null;
		try
		{
			fileOutputStream = new FileOutputStream(name);

			dataOutputStream = new DataOutputStream(fileOutputStream);

			dataInputStream = new DataInputStream(input);

			dataOutputStream.write(dataInputStream.readAllBytes());

			dataOutputStream.flush();

		}
		catch (IOException exception)
		{
			String message = "Error";
			log.error(message, exception);
		}
		finally
		{
			if (dataOutputStream != null)
			{
				try
				{
					dataOutputStream.close();
				}
				catch (IOException exception)
				{
					String message = "Error";
					log.error(message, exception);
				}
			}

			if (fileOutputStream != null)
			{
				try
				{
					fileOutputStream.close();
				}
				catch (IOException exception)
				{
					String message = "Error";
					log.error(message, exception);
				}
			}
		}
	}

	/**
	 * Method closeHttpClientResponse
	 * 
	 * @param httpClient
	 * @param response
	 * @throws ReaktorClientException
	 */
	private void closeHttpClientResponse(CloseableHttpClient httpClient, CloseableHttpResponse response)
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
			}
		}
	}

	/**
	 * Method actionsBlockDisp
	 * 
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 * @throws ComputerError
	 */
	private void actionsBlockDisp(String usbName) throws ComputerError
	{
		try
		{
//			List<Peripheral> blockDispositives = new ArrayList<Peripheral>();
//			for (int i = 0; i < actionsToDo.getBlockDispositives().size(); i++)
//			{
//				Peripheral peri = actionsToDo.getBlockDispositives().get(i);
//				peri.setOpen(false);
//				blockDispositives.add(peri);
//			}
			log.info("DISPOSITIVES TO BLOCK : " + usbName);

		}
		catch (Exception exception)
		{
			String error = "Error bloqueando dispositivo";
			log.error(error, exception);
			throw new ComputerError(1, error, exception);
		}
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
			rt.exec("cmd.exe /c " + command.replace("INFO_INFO", info));
		}
		catch (Exception exception)
		{
			String error = "Error ejecutando el comando " + command;
			log.error(error, exception);
			throw new ComputerError(1, error, exception);
		}
	}

	/**
	 * Method actionsCfgWifiFile
	 * 
	 * @param statusList
	 * @param actionsToDo
	 * @throws ComputerError
	 */
	private void actionsCfgWifiFile(String info, TaskDTO taskDTO, String serialNumber) throws ComputerError
	{
		try
		{

			downloadFile(".\\confWIFI", taskDTO, serialNumber);
			// --- IF THE FILE EXISTS AND IS A FILE ---
			executeCommand(taskDTO.getCommandWindows(), taskDTO.getInfo());
		}
		catch (Exception exception)
		{
			String error = "Error configurando wifi";
			log.error(error, exception);
			throw new ComputerError(2, error, exception);
		}

	}

	/**
	 * Method savingMonitorizationYmlCfg
	 * 
	 * @param computerMonitorizationYml
	 * @throws IOException
	 */
	private void savingMonitorizationYmlCfg(Map<String, String> computerMonitorizationYml) throws IOException
	{
		if (computerMonitorizationYml != null)
		{
			// --- OPCION RAW , NUEVO YML CON LA INFO ---
			PrintWriter printWriter = new PrintWriter(new FileWriter("./src/main/resources/monitorization.yml"));
			printWriter.print("ComputerMonitorization:\n" + "  andaluciaId: \""
					+ computerMonitorizationYml.get("andaluciaId") + "\"\n" + "  computerNumber: \""
					+ computerMonitorizationYml.get("computerNumber") + "\"\n" + "  serialNumber: \"sn12345577\"");
			printWriter.flush();
			printWriter.close();
		}
	}

	/**
	 * Method updateComputerNumber
	 * 
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 * @param computerMonitorizationYml
	 * @throws ComputerError
	 */
	private void updateComputerNumber(String serialNumber) throws ComputerError
	{
		try
		{
			Map<String, String> computerMonitorizationYml = this.openMap();
			log.info("PDATE COMPUTER NUMBER ID");
			computerMonitorizationYml.put("computerNumber", serialNumber);
			this.savingMonitorizationYmlCfg(computerMonitorizationYml);
		}
		catch (Exception exception)
		{
			String error = "Error cambiando ComputerNumber";
			log.error(error, exception);
			throw new ComputerError(2, error, exception);
		}
	}

	/**
	 * Method updateAndaluciaId
	 * 
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 * @param computerMonitorizationYml
	 * @throws ComputerError
	 */
	private void updateAndaluciaId(String id) throws ComputerError
	{
		try
		{
			Map<String, String> computerMonitorizationYml = this.openMap();
			log.info("UPDATE ANDALUCIA ID");
			computerMonitorizationYml.put("andaluciaId", id);
			this.savingMonitorizationYmlCfg(computerMonitorizationYml);
		}
		catch (Exception exception)
		{
			String error = "Error cambiando AndaluciaID";
			log.error(error, exception);
			throw new ComputerError(1, error, exception);
		}
	}

	/**
	 * Method updateSerialNumber
	 * 
	 * @param statusList
	 * @param serialNumber
	 * @param actionsToDo
	 * @param computerMonitorizationYml
	 * @throws ComputerError
	 */
	private void updateSerialNumber(String serialNumber) throws ComputerError
	{
		try
		{
			Map<String, String> computerMonitorizationYml = this.openMap();
			log.info("UPDATE SERIAL NUMBER ID");
			computerMonitorizationYml.put("serialNumber", serialNumber);
			this.savingMonitorizationYmlCfg(computerMonitorizationYml);
		}
		catch (Exception exception)
		{
			String error = "Error cambiando ComputerNumber";
			log.error(error, exception);
			throw new ComputerError(2, error, exception);
		}
	}

	/**
	 * Method openMap
	 * 
	 * @return
	 * @throws ComputerError
	 */
	private Map<String, String> openMap() throws ComputerError
	{
		try
		{
			InputStream inputStream = new FileInputStream(new File("./src/main/resources/monitorization.yml"));
			Yaml yaml = new Yaml();

			Map<String, Object> yamlMap = yaml.load(inputStream);

			return (Map<String, String>) yamlMap.get("ComputerMonitorization");
		}
		catch (FileNotFoundException e)
		{
			String error = "Error configurando wifi";
			log.error(error, e);
			throw new ComputerError(2, error, e);
		}
	}

	/**
	 * this method make a screenshot and send it
	 * 
	 * @throws ReaktorClientException
	 */
	public void getAndSendScreenshot(TaskDTO task) throws ReaktorClientException
	{
		String serialNumber = this.reaktor.getMotherboard().getComputerSerialNumber();
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;

		try
		{
			httpClient = HttpClients.createDefault();

			HttpPost request = new HttpPost("http://localhost:8084/computers/send/screenshot");
			request.setHeader("serialNumber", serialNumber);
			request.setHeader("dateLong", String.valueOf(task.getDate().getTime()));

			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

			// RECOGEMOS COMO ARRAY DE BYTES Y PONEMOS BINARY BODY
			byte[] imageBytes = Files.readAllBytes(Paths.get("./screen.png"));

			// SE PONE EL NOMBRE , screenshot, PONEMOS EL ARRAY DE BYTES , EL TIPO EN BINERY
			// Y EL NOMBRE DEL FICHERO screen.png
			builder.addBinaryBody("screenshot", imageBytes, ContentType.DEFAULT_BINARY, "screen.png");

			HttpEntity entity = builder.build();
			request.setEntity(entity);

			response = httpClient.execute(request);
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
		catch (Exception exception)
		{
			String error = "Error making the screenshot";
			log.error(error, exception);
			throw new ReaktorClientException(exception);
		}
		finally
		{
			closeHttpClientResponse(httpClient, response);
		}

	}
}
