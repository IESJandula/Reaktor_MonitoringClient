package es.iesjandula.reaktor.monitoring_client.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.iesjandula.reaktor.exceptions.ComputerError;
import es.iesjandula.reaktor.models.Configuration;
import es.iesjandula.reaktor.monitoring_client.utils.exceptions.ConstantsErrors;
import es.iesjandula.reaktor.monitoring_client.utils.exceptions.ParametersParserException;
import es.iesjandula.reaktor.monitoring_client.utils.exceptions.ReaktorClientException;
import lombok.extern.slf4j.Slf4j;

/**
 * - CLASS - This class is used to execute the action of the arguments
 */
@Service
@Slf4j
public class ActionsArguments
{
	/** Attribute writeFiles */
	@Autowired
	private WriteFiles writeFiles;

	/**
	 * Method writeConfiguration
	 * 
	 * @param args
	 * @throws ReaktorClientException
	 * @throws ParametersParserException
	 */

	public void writeConfiguration(String[] args) throws ReaktorClientException, ParametersParserException
	{

		// Creation new configuration
		Configuration configuration = new Configuration();
		try
		{ // Getting the configuration values from ParametersParser
			// Calling to ParametersParser for check arguments

			String[] args2 = this.removeIfAwsParameterExists(args);

			configuration = new ParametersParser().parse(args2);
		} catch (ParametersParserException excep)
		{
			// Exception if exist any error on argumetns
			String errorString = ConstantsErrors.ERROR_PARSING_ARGUMENTS + " or "
					+ ConstantsErrors.ERROR_ARGUMENTS_NOT_FOUND;
			log.error(errorString);
			throw new ParametersParserException(errorString, excep);
		}

		// Checking the configuration attribute values
		this.checkConfiguration(configuration);
		this.checkContentConfiguration(configuration);

		// Getting the configuration values and transform to JSON
		this.writeFiles.escribirResultadoJson(configuration);
	}

	/**
	 * Method removeIfAwsParameterExists , metodo para borrar el parametro para el
	 * yaml de AWS
	 * 
	 * @param args
	 * @return String[]
	 */
	private String[] removeIfAwsParameterExists(String[] args)
	{
		// Lista temporal para obtener los argumentos
		List<String> temporalArgumentsList = new ArrayList<String>();

		// Bucle para borrar el parametro --spring.profiles.active=AWS si existe
		for (int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			if (!arg.equals("--spring.profiles.active=AWS"))
			{
				temporalArgumentsList.add(arg);
			}
		}

		// Array para almacenar los argumentos otra vez
		String[] args2 = new String[temporalArgumentsList.size()];

		// Ponemos cada argumento en el array
		for (int i = 0; i < temporalArgumentsList.size(); i++)
		{
			args2[i] = temporalArgumentsList.get(i);
		}
		return args2;
	}

	private void checkContentConfiguration(Configuration configuration) throws ReaktorClientException
	{

		if (configuration.getTeacher() == null || configuration.getTeacher().isEmpty())
		{

			if(configuration.getFloor() == null) {
				throw new ReaktorClientException("1", "No floor");
			}
			
			if(configuration.getTrolley() == null || configuration.getTrolley().isEmpty()) {
				if(configuration.getClassroom() == null || configuration.getClassroom().isEmpty()) {
					throw new ReaktorClientException("2", "No Classroom");
				}
			}
			
			if(configuration.getClassroom() == null || configuration.getClassroom().isEmpty()) {
				if(configuration.getTrolley() == null || configuration.getTrolley().isEmpty()) {
					throw new ReaktorClientException("3", "No Trolley");
				}
			}
		} else
		{
			if(configuration.getFloor() != null || configuration.getTrolley() != null || !configuration.getTrolley().isEmpty() ||
					configuration.getClassroom() != null || !configuration.getClassroom().isEmpty()) {
				throw new ReaktorClientException("4", "No teacher only");
			}

		}

	}

	/**
	 * Method checkConfiguration
	 * 
	 * @param configuration
	 */
	public void checkConfiguration(Configuration configuration)
	{
		// If any attribute of configuration is null or empty string ("") , set "Unknow"
		// string value.

		if (configuration.getClassroom() == null || configuration.getClassroom().isEmpty())
		{
			configuration.setClassroom(Constants.UNKNOWN);
		}

		if (configuration.getTeacher() == null || configuration.getTeacher().isEmpty())
		{
			configuration.setTeacher(Constants.UNKNOWN);
		}

		if (configuration.getTrolley() == null || configuration.getTrolley().isEmpty())
		{
			configuration.setTrolley(Constants.UNKNOWN);
		}

		if (configuration.getAndaluciaId() == null || configuration.getAndaluciaId().isEmpty())
		{
			configuration.setAndaluciaId(Constants.UNKNOWN);
		}
		if (configuration.getComputerNumber() == null || configuration.getComputerNumber().isEmpty())
		{
			configuration.setComputerNumber(Constants.UNKNOWN);
		}
		if (configuration.getComputerSerialNumber() == null || configuration.getComputerSerialNumber().isEmpty())
		{
			configuration.setComputerSerialNumber(Constants.UNKNOWN);
		}

		if (configuration.getIsAdmin() == null)
		{
			configuration.setIsAdmin(false);
		}
	}
}
