package es.iesjandula.reaktor.monitoring_client.utils;

import org.springframework.stereotype.Service;

import es.iesjandula.reaktor.monitoring_client.utils.exceptions.ReaktorClientException;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class CommandExecutor
{
    public String executeCommand(String command) throws ReaktorClientException
    {
        StringBuilder output = new StringBuilder();
        Process p;
        try
        {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null)
            {
                output.append(line).append("\n");
            }
        }
        catch (InterruptedException interruptedException)
        {
            throw new ReaktorClientException("Error al ejecutar el comando", "500" ,interruptedException);
        }
        catch (Exception exception)
        {
            throw new ReaktorClientException("", "500");
        }
        return output.toString();
    }
}
