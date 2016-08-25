package com.ztequantum.comm;

/**
 * Created by zhangchao on 2016/7/14.
 */


import gnu.io.*;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Enumeration;
import java.util.TooManyListenersException;

public class SerialPort implements Runnable, SerialPortEventListener {

    private String appName = "comm collector";
    private int timeout = 2000;//open waiting time
    private int threadTime = 0;

    private CommPortIdentifier commPort;
    private gnu.io.SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;

    //TCP relative
    private Socket socket ;

    /* Logger */
    private Logger logger = LogManager.getLogger(SerialPort.class);

    /**
     * show all list port
     */
    @SuppressWarnings("rawtypes")
    public void listPort(){
        CommPortIdentifier cpid;
        Enumeration en = CommPortIdentifier.getPortIdentifiers();

        if(logger.isDebugEnabled()){
            logger.debug("start comm port ...");
            logger.debug("now to list all Port of this PC:" +en);
        }

        while(en.hasMoreElements()){
            cpid = (CommPortIdentifier)en.nextElement();
            if(cpid.getPortType() == CommPortIdentifier.PORT_SERIAL){
                if(logger.isDebugEnabled()){
                    logger.debug(cpid.getName() + ", " + cpid.getCurrentOwner());
                }
            }
        }
    }


    /**
     * select a port eg:COM1
     * @param portName
     */
    @SuppressWarnings("rawtypes")
    public void selectPort(String portName) throws PortInUseException {

        this.commPort = null;
        CommPortIdentifier cpid;
        Enumeration en = CommPortIdentifier.getPortIdentifiers();

        while(en.hasMoreElements()){
            cpid = (CommPortIdentifier)en.nextElement();
            if(cpid.getPortType() == CommPortIdentifier.PORT_SERIAL
                    && cpid.getName().equals(portName)){
                this.commPort = cpid;
                break;
            }
        }

        openPort();
    }

    /**
     * open the SerialPort
     */
    private void openPort() throws PortInUseException {
        if(commPort == null)
            log(String.format("Can not find the name:'%1$s'comm port!", commPort.getName()));
        else{
            log("Comm Port Select Success,Current Port:"+commPort.getName()+",Instantiation SerialPort:");
            serialPort = (gnu.io.SerialPort)commPort.open(appName, timeout);
            log("Instant SerialPort Success!");

        }
    }

    /**
     * check the port whether or not opened
     */
    private void checkPort(){
        if(commPort == null)
            throw new RuntimeException("Have not select Port ,pls use " +
                    "selectPort(String portName) function select port");

        if(serialPort == null){
            throw new RuntimeException("SerialPort invalid!");
        }
    }

    public boolean checkPortOpen(){
        if(commPort == null){
            return false;
        }
        if(serialPort == null){
            return false;
        }
        return true;
    }

    /**
     * write data to comm port , select port first , confirm the serial port whether or not have opened
     * @param message
     */
    public void write(byte[] message) {
        checkPort();

        try{
            outputStream = new BufferedOutputStream(serialPort.getOutputStream());
        }catch(IOException e){
            throw new RuntimeException("fetch the port OutputStream error:"+e.getMessage());
        }

        try{
            outputStream.write(message);
            log("Message sent to comm port successful:" + message);
        }catch(IOException e){
            throw new RuntimeException("Send Message Error:"+e.getMessage());
        }finally{
            try{
                outputStream.close();
            }catch(Exception e){
            }
        }
    }

    /**
     * listening the response data
     * @param time time 0 listening all the time
     */
    public void startRead(int time){
        checkPort();

        try{
            inputStream = new BufferedInputStream(serialPort.getInputStream());
        }catch(IOException e){
            throw new RuntimeException("fetch the port InputStream error:"+e.getMessage());
        }

        try{
            serialPort.addEventListener(this);
        }catch(TooManyListenersException e){
            throw new RuntimeException(e.getMessage());
        }

        serialPort.notifyOnDataAvailable(true);

        log(String.format("Start listening '%1$s'data --------------", commPort.getName()));
        //Thread Timer close serialPort
        if(time > 0){
            this.threadTime = time*1000;
            Thread t = new Thread(this);
            t.start();
            log(String.format("Listener will be closed on %1$d seconds later", threadTime));
        }
    }


    /**
     * set socket channel to serial port
     * @param socket
     */
    public void setSocket(Socket socket){
        this.socket = socket;
    }

    /**
     * close the SerialPort
     */
    public void close(){
        serialPort.close();
        serialPort = null;
        commPort = null;
    }

    /**
     * log here
     * @param msg
     */
    public void log(String msg){
        if(logger.isDebugEnabled()){
            logger.debug(appName+" --> "+msg);
        }
    }


    /**
     * Listening Event, Processing the Event here
     * @param arg0
     */
    public void serialEvent(SerialPortEvent arg0) {
        switch(arg0.getEventType()){
            case SerialPortEvent.BI:/*Break interrupt*/
            case SerialPortEvent.OE:/*Overrun error*/
            case SerialPortEvent.FE:/*Framing error*/
            case SerialPortEvent.PE:/*Parity error*/
            case SerialPortEvent.CD:/*Carrier detect*/
            case SerialPortEvent.CTS:/*Clear to send*/
            case SerialPortEvent.DSR:/*Data set ready*/
            case SerialPortEvent.RI:/*Ring indicator*/
            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:/*Output buffer is empty*/
                break;
            case SerialPortEvent.DATA_AVAILABLE:/*Data available at the serial port*/
                byte[] readBuffer = new byte[1024];
                try {
                    int length = 0 ;
                    while( -1 !=(length = inputStream.read(readBuffer, 0, readBuffer.length))){
                        String hexString = "";
                        byte[] b = new byte[length];
                        for(int i=0;i<length;i++) {
                            b[i] = readBuffer[i];
                            hexString += Integer.toHexString((0x000000ff & readBuffer[i]) | 0xffffff00).substring(6).toUpperCase();
                            if(i<length-1){hexString += " ";}
                        }
                        if(logger.isDebugEnabled()){
                            logger.debug("COMM PORT Received: " + hexString);
                        }
                        //log("1/2 Receive the data from comm port");
                        //log("2/2 ");

                        //Transfer the data to TCP Server
                        if(this.socket!=null) {
                            OutputStream socketOutputStream = this.socket.getOutputStream();
                            socketOutputStream.write(b);
                        }
                    }


                } catch (IOException e) {
                    logger.error(e,e);
                }
        }
    }

    /**
     * Thread run force on Close serial Port
     */
    public void run() {
        try{
            Thread.sleep(threadTime);
            serialPort.close();
            log(String.format("Port'%s'Listener closed!", commPort.getName()));
        }catch(Exception e){
            logger.error(e,e);
        }
    }
}
