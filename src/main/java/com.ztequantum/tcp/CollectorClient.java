package com.ztequantum.tcp;

import org.apache.log4j.PropertyConfigurator;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by zhangchao on 2016/7/15.
 */
public class CollectorClient {

    //config parameter
    private static String ip;
    private static int port ;
    private static String collectorId;
    private static int timeInterval;
    private static int readTimeInterval;
    private static int retryConnectTimeInterval;

    //comm port
    private static String commPortName; //windows: COM2/COM3  -  linux: /dev/ttyS1   /dev/ttyS2

    //=------------------=
    public static final String CONFIG_FILE_PATH = System.getProperty("user.dir") + "/config/";
    /**
     * Collector Portal
     * @param args
     */
    public static void main(String[] args){
        //logger config
        Properties logProps = new Properties();
        Properties configProps = new Properties();
        try {
            logProps.load(CollectorClient.class.getClassLoader().getResourceAsStream("log4j.properties")); //jar
            configProps.load(new FileInputStream(CONFIG_FILE_PATH + "config.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        PropertyConfigurator.configure(logProps);
        ip = configProps.getProperty("ip");
        port = Integer.parseInt(configProps.getProperty("port"));
        collectorId = configProps.getProperty("collectorId");
        timeInterval = Integer.parseInt(configProps.getProperty("timeInterval"));
        readTimeInterval = Integer.parseInt(configProps.getProperty("readTimeInterval"));
        retryConnectTimeInterval = Integer.parseInt(configProps.getProperty("retryConnectTimeInterval"));
        commPortName = configProps.getProperty("commPortName");

        //collector
        CollectorService collector = new CollectorService();
        collector.startup(ip,port,collectorId,timeInterval,readTimeInterval,retryConnectTimeInterval,commPortName);
    }
}
