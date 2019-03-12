/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import jssc.SerialPort;

/**
 *
 * @author cpt-kirk
 */
public abstract class SimpleServer
{

	private final int port;
	private ServerSocket serverSocket = null;
	private HandleRequestThread hreqthread = null;

	private final ExecutorService exe = Executors.newWorkStealingPool();

	public SimpleServer(int port)
	{
		this.port = port;
	}

	public void start() throws IOException
	{
		if (serverSocket == null)
			serverSocket = new ServerSocket(port);
		serverSocket.setSoTimeout(500);
		(hreqthread = new HandleRequestThread()).start();
	}

	public void stop() throws Exception
	{
		hreqthread.interrupt();
		try
		{
			hreqthread.join(2000);
		}
		catch (InterruptedException ex)
		{
			throw new Exception(ex.getMessage());
		}
		if (serverSocket != null)
			serverSocket.close();
		serverSocket = null;
	}

	private Socket listen() throws IOException
	{
		return serverSocket.accept();
	}

	private String readRequest(Socket socket) throws IOException
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf8"));
		String line = reader.readLine();

		return line;
	}

	protected abstract String createResponse(String request);

	private void sendResponse(String response, Socket socket) throws IOException
	{
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "utf8"));
		writer.write(response);
		writer.flush();
		socket.shutdownOutput();
	}

	private void handleRequest() throws Exception
	{
		Socket socket = listen();

		exe.submit(() ->
		{
			try
			{
				final String request = readRequest(socket);
				System.out.println("Anfrage eingetroffen: \"" + request + "\"");
				final String response = createResponse(request);
				System.out.println("Antwort erzeugt: " + response);
				sendResponse(response, socket);
				System.out.println("Antwort gesendet");
				System.out.println("Bearbeitung der Anfrage erfolgreich abgeschlossen\n");
			}
			catch (IOException ex)
			{
				//throw new Exception(ex.getMessage());
				ex.printStackTrace();
			}
		});
	}

	private class HandleRequestThread extends Thread
	{
		@Override
		public void run()
		{
			while (!isInterrupted())
				try
				{
					handleRequest();
				}
				catch (SocketTimeoutException soex)
				{
					;
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}
		}
	}
}
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
*/
