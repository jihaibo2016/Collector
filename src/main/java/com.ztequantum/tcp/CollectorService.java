package com.ztequantum.tcp;

import com.ztequantum.comm.SerialPort;
import com.ztequantum.kyuden.KyudenHttpsService;
import com.ztequantum.util.DateUtils;
import gnu.io.PortInUseException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import sun.rmi.runtime.Log;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Created by zhangchao on 2016/7/18.
 */
public class CollectorService {
    private Socket socket;
    private  SerialPort sp;
    private Thread commThread;
    private Thread tcpThread;
    private Thread httpsThread;

    private int readTimeInterval;
    private int retryConnectTimeInterval;

    /* Logger */
    private Logger logger = LogManager.getLogger(CollectorService.class);

    /**
     * startup function
     * @param server
     * @param port
     * @param collectorId
     * @param timeInterval
     */
    public void startup(final String server, final int port,final String collectorId, final int timeInterval,
                        final int readTimeInterval,
                        final int retryConnectTimeInterval,final String commPortName) {

        this.readTimeInterval = readTimeInterval;
        this.retryConnectTimeInterval = retryConnectTimeInterval;

        //Thread Comm port connect
        commThread = new Thread(){
            public void run() {
                while(true){
                    try {
                        connectSerialPort(commPortName);
                        sleep(timeInterval);
                    } catch (Exception e) {
                        logger.error(e,e);
                    }
                }
            }
        };
        commThread.start();

        //Thread Tcp connect
        tcpThread = new Thread() {
            public void run() {
                while (true) {
                    try {
                        //when sp is null ,then wait the comm port is open
                        if(logger.isDebugEnabled()){
                            logger.debug("*********************************");
                        }
                        if (sp==null){
                            sleep(5000);
                            if(logger.isDebugEnabled()){
                                logger.debug("sp is null ,wait 5 seconds..");
                            }
                            continue;
                        }
                        if(!sp.checkPortOpen()) {
                            sleep(5000);
                            if(logger.isDebugEnabled()){
                                logger.debug("checkPortOpen is false ,wait 5 seconds..");
                            }
                            continue;
                        }
                        connect(server, port);

                        //send a init signal
                        InputStream in = socket.getInputStream();
                        OutputStream out = socket.getOutputStream();
                        out.write(collectorId.getBytes()); //init signal from config file
                        if(logger.isDebugEnabled()){
                            logger.debug("outputstream["+ out +"] write:"+collectorId);
                            logger.debug("Init TCP msg have already sent");
                            logger.debug("--------------------------");
                            logger.debug("Start TCP receive msg from server...");
                        }

                        byte[] buffer = new byte[2048];
                        int length = 0 ;
                        while( -1 !=(length = in.read(buffer, 0, buffer.length))){
                            String hexString = "";
                            byte[] b = new byte[length];
                            if(logger.isDebugEnabled()){
                                logger.debug("buffer length: " + length);
                            }
                            for(int i=0;i<length;i++) {
                                b[i] = buffer[i];
                                hexString += Integer.toHexString((0x000000ff & buffer[i]) | 0xffffff00).substring(6).toUpperCase();
                                if(i<length-1){hexString += " ";}
                            }
                            //System.out.println( new String(buffer,0 ,length ));
                            if(logger.isDebugEnabled()){
                                logger.debug("Received: " + hexString);
                            }
                            //Transfer data to Comm Port
                            connectSerialPortInTCP(b, commPortName);
                        }
                        in.close();
                        out.close();
                        //close socket
                        if(socket.isConnected()) {socket.close();}
                        if(logger.isDebugEnabled()){
                            logger.debug("done, ready sleep..");
                        }
                        sleep(timeInterval);
                    } catch (InterruptedException e) {
                        // You may or may not want to stop the thread here
                        // tryToReconnect = false;
                        logger.error(e,e);
                    } catch (IOException e) {
                        //logger.warn("Server is offline");
                        if(logger.isDebugEnabled()){
                            logger.debug("Session IOException disconnection");
                        }
                        //close socket
                        if(socket.isConnected()) {
                            try {
                                socket.close();
                            } catch (IOException e1) {
                                logger.error("IOException close socket",e);
                            }
                        }
                        logger.error(e,e);
                        connect(server, port);
                    }finally {
                        if (socket != null) {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                socket = null;
                                if(logger.isDebugEnabled()){
                                    logger.debug("finally socket exception");
                                }
                            }
                        }
                    }
                }
            }
        };
        //delay start the thread
        try {
            tcpThread.sleep(5000);
        } catch (InterruptedException e) {
            logger.error(e,e);
        }
        tcpThread.start();

        //Thread Https connect
        httpsThread = new Thread(){
            public void run() {
                while(true){
                    try {
                        KyudenHttpsService client = new KyudenHttpsService();
                        boolean loginFlag = false;
                        //login (only one time on startup)
                        if(loginFlag==false){
                            String flagLogin = "8888";
                            client.request(flagLogin);
                            loginFlag = true;
                            sleep(1000);
                        }
                        //update
                        String flagUpdate = "0000";
                        String updateContent = client.request(flagUpdate);
                        sleep(2000);
                        //Judge whether need to update the routine
                        String routineFlag = new KyudenHttpsService().getProperties("routine_flag");
                        if(updateContent!=null) {
                            String lastRoutineFlag = updateContent.substring(updateContent.length()-17,updateContent.length()-16);
                            if(logger.isDebugEnabled()){
                                logger.debug("lastRoutineFlag:"+lastRoutineFlag+"---routineFlag:"+routineFlag);
                            }
                            if (!routineFlag.equals(lastRoutineFlag)) {
                                String flagRoutine = "999"+lastRoutineFlag;
                                String routineContent = client.request(flagRoutine); //request the https
                                if(logger.isDebugEnabled()){
                                    logger.debug("Invoking the Routine:"+flagRoutine+"---response:"+routineContent);
                                }
                                //write the new routine flag to file
                                int nextFlag = Integer.parseInt(lastRoutineFlag) + 1;
                                if(nextFlag >= 10){
                                    nextFlag = 0;
                                }
                                new KyudenHttpsService().writeProperties("routine_flag",String.valueOf(nextFlag));
                            }
                        }
                        sleep(2000);
                        client.mergerRoutineAndUpdate();
                        //current time | next update time
                        String nextUpdateTime = new KyudenHttpsService().getProperties("next_update_time");
                        long millseconds = DateUtils.millsecondsInterval(nextUpdateTime, DateUtils.getCurrentTimeyyyyMMddHHmmss());
                        if(millseconds > 0){
                            sleep(millseconds);
                        }else{
                            sleep(10000);
                        }
                    } catch (Exception e) {
                        logger.error(e,e);
                    }
                }
            }
        };
        httpsThread.start();

    }


    /**
     * connectSerialPortInTCP portal
     * @param b
     * @param commPortName
     * @throws PortInUseException
     */
    private void connectSerialPortInTCP(byte[] b, String commPortName){
        try {
            if(logger.isDebugEnabled()){
                logger.debug("start connectSerialPortInTCP...");
            }
            if (sp == null) {sp = new SerialPort();}
            if(!sp.checkPortOpen()){
                if(logger.isDebugEnabled()){
                    logger.debug("start comm port in TCP Thread...");
                }
                sp.selectPort(commPortName);
                sp.write(b);
                sp.startRead(0);//start the listener
            }else{
                sp.write(b);
            }
        } catch (PortInUseException e) {
            //retry connect to comm port
            logger.error(e,e);
            try {
                commThread.sleep(retryConnectTimeInterval);
                connectSerialPort(commPortName);
            } catch (InterruptedException e1) {
                logger.error(e1,e1);
            }
        }
    }

    /**
     * connect serial port
     * @param commPortName
     * @throws PortInUseException
     */
    private void connectSerialPort(String commPortName){

        try {
                if (sp == null) {sp = new SerialPort();}
                if(!sp.checkPortOpen()) {
                    if(logger.isDebugEnabled()){
                        logger.debug("start comm port ...");
                    }
                    sp.selectPort(commPortName);
                    sp.startRead(0); //start the listener
                }
            } catch (PortInUseException e) {
                //retry connect to comm port
                try {
                    commThread.sleep(retryConnectTimeInterval);
                    connectSerialPort(commPortName);
                } catch (InterruptedException e1) {
                    logger.error(e1,e1);
                }
            }
    }

    /**
     * connect function
     * @param server
     * @param port
     */
    private void connect(String server, int port){
        if(logger.isDebugEnabled()) {
            logger.debug("connect to " + server + ":" + port + "...");
        }
        try {
            socket = new Socket(server, port);
            //set so read timeout
            socket.setSoTimeout(readTimeInterval);
            if(sp!=null) {
                sp.setSocket(socket); //set socket to serial port
                if(logger.isDebugEnabled()) {
                    logger.debug("The socket in SerialPort ,set socket");
                }
            }
        } catch (UnknownHostException e) {
            //logger.error(e, e);
            logger.error(e,e);
        } catch (IOException e) {
            //logger.error(e, e);
            logger.error(e,e);
            try {
                tcpThread.sleep(retryConnectTimeInterval);
                connect(server,port);   //retry connect
            } catch (InterruptedException e1) {
                logger.error(e1,e1);
            }
        }
    }

}
