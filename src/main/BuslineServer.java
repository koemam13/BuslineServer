package main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;


public class BuslineServer {

    private static jssc.SerialPort serialPort;
    static int currentBusline = 123;
    private int red = 255;
    private int green = 100;
    private int blue = 0;
    private String serialMessage;
    static SerialPortEventListener spel;
    static int number3;
    private static File namefile;

    private EchoServer_1 echoServer;
    private static String name;
    private static boolean portUsed = false;


    private String startserver () throws SerialPortException {
        try {
            (echoServer = new EchoServer_1()).start();
            return "Server gestartet";
        }
        catch (SocketException ex) {
            return "Server konnte nicht gestartet werden\n" + ex.toString();
        }
    }


    public static void main (String[] args) {

        String errMsg;
        String serialPortName = "/dev/ttyACM0";
        namefile = new File("/home/pi/name.txt");

        try {
            BufferedReader reader1 = new BufferedReader(new FileReader(namefile));
            String line = reader1.readLine();
            if (line.isEmpty()) {
                name = "unknown";
            }
            else {
                name = line;
            }
        }
        catch (FileNotFoundException ex) {
            name = "unknown";
        }
        catch (IOException ex) {
            name = "unknown";
        }

        System.out.println(name);

        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("serial")) {
                    String s[] = args[i].split("=");
                    serialPortName = s[1];
                }
            }

            serialPort = new jssc.SerialPort(serialPortName);
            serialPort.openPort();
            serialPort.setParams(SerialPort.BAUDRATE_57600, SerialPort.DATABITS_8,
                                 SerialPort.STOPBITS_2, SerialPort.PARITY_NONE);

            spel = new PortReader();

            serialPort.addEventListener(spel, SerialPort.MASK_RXCHAR);

            if (serialPort.isOpened()) {
                System.out.println("Serielle Schnittstelle geöffnet");
            }

            BuslineServer buslinienserver = new BuslineServer();
            String test = buslinienserver.startserver();
            System.out.println(test);

        }
        catch (SerialPortException ex) {
            System.out.println(ex.toString());
        }
    }


    private static class PortReader implements SerialPortEventListener {

        @Override
        public void serialEvent (SerialPortEvent event) {
            if (event.isRXCHAR()) {
                try {
                    String receivedData = serialPort.readString(event.getEventValue());

                    if (receivedData.startsWith("CBL")) {
                        String split[] = receivedData.split(" ");
                        if (split.length < 2) {
                            serialPort.writeString("CBL ERR\n");
                        }
                        if (split.length > 1) {
                            currentBusline = Integer.parseInt(split[1].trim());
                            serialPort.writeString("Check " + currentBusline + "\n");
                        }
                    }
                    if (receivedData.startsWith("SBL")) {
                        String split[] = receivedData.split(" ");

                        number3 = Integer.parseInt(split[1].trim());
                    }
                    if (receivedData.trim().startsWith("sh")) {
                        System.out.println("Shutdown system");
                        Runtime.getRuntime().exec("sudo poweroff");
                    }
                    if (receivedData.trim().startsWith("wm")) {
                        serialPort.writeString("ack \n");
                        System.out.println("sending ack");
                        Runtime.getRuntime().exec("sudo systemctl start hostapd");
                        Runtime.getRuntime().exec("sudo systemctl start dnsmasq");
                    }
                    if (receivedData.trim().startsWith("nwm")) {
                        serialPort.writeString("ack \n");
                        System.out.println("sending ack");
                        Runtime.getRuntime().exec("sudo systemctl stop hostapd");
                        Runtime.getRuntime().exec("sudo systemctl stop dnsmasq");
                    }
                    System.out.println("Received response: " + receivedData);
                }
                catch (SerialPortException ex) {
                    System.out.println("Error in receiving string from COM-port: " + ex);
                }
                catch (IOException ex) {
                    Logger.getLogger(BuslineServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }


    private class EchoServer_1 extends Thread {

        private DatagramSocket socket;
        private boolean running;
        private byte[] buf = new byte[256];


        public EchoServer_1 () throws SocketException {
            socket = new DatagramSocket(8150);
        }


        @Override
        public void run () {
            running = true;

            while (running) {
                try {
                    buf = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    packet = new DatagramPacket(buf, buf.length, address, port);
                    String received = new String(packet.getData(), 0, packet.getLength());
                    buf = new byte[256];
                    System.out.println("Received: " + received);

                    if (received.equals("end")) {
                        running = false;
                        continue;
                    }
                    String response = createResponse(received.trim());
                    if (response.trim().equals("")) {
                        continue;
                    }
                    buf = response.getBytes();
                    System.out.println(new String(buf));
                    packet = new DatagramPacket(buf, buf.length, address, port);
                    socket.send(packet);
                }
                catch (IOException iOException) {
                    System.out.println(iOException.getClass().getSimpleName() + ": " + iOException.getMessage());
                }
            }
            socket.close();
        }


        private String createResponse (String request) throws UnknownHostException {
            String response;
            String[] requestSplit;
            System.out.println(request);
            if (request == null) {
                request = "IREQ";
            }
            requestSplit = request.split(" ");

            if (requestSplit[0].equals("SBL") && requestSplit.length == 5) {
                int number = Integer.parseInt(requestSplit[1]);
                int _red = Integer.parseInt(requestSplit[2]);
                int _green = Integer.parseInt(requestSplit[3]);
                int _blue = Integer.parseInt(requestSplit[4]);
                if (_red >= 0 && _red <= 255 && _green >= 0 && _green <= 255 && _blue >= 0 && _blue <= 255 && number >= 0) {

                    currentBusline = number;
                    red = _red;
                    green = _green;
                    blue = _blue;

                    serialMessage = String.format("%03d %03d %03d %03d ", red, green, blue, currentBusline);

                    System.out.println(serialMessage);
                    try {
                        serialPort.writeString(serialMessage + "\n");
                        TimeUnit.MILLISECONDS.sleep(60);

                    }
                    catch (Exception ex) {
                        System.out.println(ex.toString());
                        ex.printStackTrace();
                    }

                    if (number3 != currentBusline) {
                        System.out.println("Buslinie wird erneut gesendet");
                        try {
                            serialPort.writeString(serialMessage + "\n");
                        }
                        catch (SerialPortException ex) {
                            System.out.println(ex.toString());
                            ex.printStackTrace();
                        }
                    }
                    response = String.format("SET %03d %03d %03d %03d", number, red, green, blue);
                }
                else {
                    response = "IREQ";
                }
            }
            else if (requestSplit[0].equals("CBL")) {

                response = String.format("CUR %03d %03d %03d %03d", currentBusline, red, green, blue);
            }
            else if (requestSplit[0].equals("FBS")) {
                try {
                    response = "BLS " + Inet4Address.getLocalHost().getHostName();
                }
                catch (UnknownHostException ex) {
                    response = "BLS";
                }
            }
            else if (request.equals("GSN " + name)) {
                response = String.format("SNA " + name);
            }
            else if (requestSplit[0].equals("SSN")) {
                String checkname;
                try {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(namefile));

                    writer.write(requestSplit[1]);
                    writer.close();

                    BufferedReader reader = new BufferedReader(new FileReader(namefile));

                    checkname = reader.readLine().trim();
                    response = "SNC " + checkname;

                }
                catch (Exception e) {
                    response = "SNC ";
                }
            }
            else {
                if (!request.equals("GSN " + name)) {
                    response = "";
                }
                else {
                    response = "IREQ";
                }
            }
            return response;
        }
    }

}
