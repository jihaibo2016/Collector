package com.ztequantum.kyuden;

import com.ztequantum.util.DateUtils;
import com.ztequantum.util.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.contrib.ssl.AuthSSLProtocolSocketFactory;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.log4j.PropertyConfigurator;

/**
 * Created by zhangchao on 2016/8/1.
 */
public class KyudenHttpsService {

    private String httpsIP = "re-ene.kyuden.co.jp";
    private int httpsPort = 443;
    private String httpsURI = "/scheduleSend/";
    private String httpsJKS = "c:/reene.jks";
    private String httpsTrustJKS = "c:/reene.jks";

    /* Logger */
    private Logger logger = LogManager.getLogger(KyudenHttpsService.class);

    public String request(String flag) throws IOException{

        //23498213471000000000000017
        //09073330000001000000010014
        String controlHex = null;
        String soapRequestData = "power_plant_id=09073330000001000000010014&mac_address=012345ABCDEF&schedule_kbn="+flag;
        ProtocolSocketFactory psf = new AuthSSLProtocolSocketFactory(new URL("file:" + httpsJKS),
                "111111", new URL("file:" + httpsTrustJKS), "111111");
        Protocol authhttps = new Protocol("https", psf, httpsPort);

        //header
        List <Header> headers = new ArrayList<Header>();
        headers.add(new Header("User-Agent", "Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 5.1)"));


        HttpClient httpClient = new HttpClient();
        //httpClient.getState().setCredentials(AuthScope.ANY, creds);
        httpClient.getHostConfiguration().setHost(httpsIP, httpsPort, authhttps);
        httpClient.getHostConfiguration().getParams().setParameter("http.default-headers", headers);

        if(logger.isDebugEnabled()){
            logger.debug("==================================");
            logger.debug("Request:");
            logger.debug(soapRequestData);
            logger.debug("==================================");
        }

        PostMethod postMethod = new PostMethod(httpsURI);

        StringRequestEntity requestEntity = new StringRequestEntity(soapRequestData,"application/x-www-form-urlencoded","UTF-8");
        postMethod.setRequestEntity(requestEntity);

        int statusCode = httpClient.executeMethod(postMethod);
        if(statusCode == 200) {
            if(logger.isDebugEnabled()){
                logger.debug("**********invoking successful*******************");
            }

            String jisInfo = new String(postMethod.getResponseBody(),"UTF-8");
            byte[] b = postMethod.getResponseBody();
            String hexString = bytesToHexString(b);
            if(logger.isDebugEnabled()){
                logger.debug("**********invoking successful*******************");
                logger.debug(hexString);
                logger.debug("=============200==200=200=200==============");
                logger.debug(jisInfo);
            }


            //the below part is for 0000 and 9990 only
            if(flag.equals("0000") || flag.startsWith("999")) {
                //0D0A0D0A  Two Enter \r\n split
                //0D0A2D2D424F554E444152592D2D  The End
                controlHex = hexString.substring(hexString.indexOf("DADA") + 4, hexString.indexOf("DA2D2D424F554E444152592D2D"));
                if (logger.isDebugEnabled()) {
                    logger.debug(controlHex);
                    logger.debug("start store the command..");
                }
                //store the https response command
                Properties kyudenProps = new Properties();
                kyudenProps.load(new FileInputStream(System.getProperty("user.dir") + "/config/system/" + "kyuden"));
                PropertyConfigurator.configure(kyudenProps);
                if (flag.equals("0000")) {
                    kyudenProps.setProperty("update", controlHex);
                } else if (flag.startsWith("999")) {
                    kyudenProps.setProperty("routine", controlHex);
                }
                kyudenProps.store(new FileOutputStream(System.getProperty("user.dir") + "/config/system/" + "kyuden"), "kyuden");
                if (logger.isDebugEnabled()) {
                    logger.debug("finish store the command");
                }
            }
        }else {
            logger.error("Error :code" + statusCode);
            String soapResponseData = postMethod.getResponseBodyAsString();
            logger.error("Error :body =============="+soapResponseData);
        }

        return controlHex;
    }


    /** merger the command **/
    public void mergerRoutineAndUpdate() throws IOException, ParseException {
        Properties kyudenProps = new Properties();
        kyudenProps.load(new FileInputStream(System.getProperty("user.dir") + "/config/system/" + "kyuden"));
        PropertyConfigurator.configure(kyudenProps);

        String routineCommand = kyudenProps.getProperty("routine");
        String updateCommand =  kyudenProps.getProperty("update");

        //next update time
        String nextUpdateTime = updateCommand.substring(updateCommand.length()-14);

        //routine flag
        String routineFlag = updateCommand.substring(updateCommand.length()-17,updateCommand.length()-16);

        //parse the content
        if(routineCommand==null || "".equals(routineCommand)){
            if(logger.isDebugEnabled()) {
                logger.debug("The File-kyuden: routine is empty!");
            }
            return;
        }

        if(updateCommand==null || "".equals(updateCommand)){
            if(logger.isDebugEnabled()) {
                logger.debug("The File-kyuden: updateCommand is empty!");
            }
            return;
        }

        routineCommand = routineCommand.substring(42,routineCommand.length()-2);
        updateCommand = updateCommand.substring(42,updateCommand.length()-17);

        String updateTime  = updateCommand.substring(0,12);
        String routineTime = routineCommand.substring(0,12);
        long halfHours = DateUtils.halfHoursInterval(updateTime, routineTime);
        String updateContent = updateCommand.substring(17);
        String fullcontent = StringUtils.replaceStringByPosition(routineCommand, updateContent, (int) (17 + (halfHours * 2)));
        if(logger.isDebugEnabled()) {
            logger.debug("Full content:"+fullcontent);
        }

        if(logger.isDebugEnabled()) {
            logger.debug("start store the full merger content command..");
        }
        //store the full merger content command
        kyudenProps.setProperty("full", fullcontent);
        //update the 'next_update_time'
        kyudenProps.setProperty("next_update_time",nextUpdateTime);
        //update the 'routine_flag'
        kyudenProps.setProperty("routine_flag",routineFlag);

        kyudenProps.store(new FileOutputStream(System.getProperty("user.dir") + "/config/system/" + "kyuden"), "kyuden");
        if(logger.isDebugEnabled()) {
            logger.debug("finish store the full merger content command");
        }
    }


    /** fetch the next update time **/
    public String getProperties(String propertiesName) throws IOException {
        if(logger.isDebugEnabled()) {
            logger.debug("getProperties: "+propertiesName);
        }

        Properties kyudenProps = new Properties();
        kyudenProps.load(new FileInputStream(System.getProperty("user.dir") + "/config/system/" + "kyuden"));
        PropertyConfigurator.configure(kyudenProps);

        String propertiesValue = kyudenProps.getProperty(propertiesName);

        if(logger.isDebugEnabled()) {
            logger.debug("finish getvalue:" + propertiesValue);
        }
        return propertiesValue;
    }

    /** write properties to file **/
    public void writeProperties(String propertiesName,String propertiesValue) throws IOException {
        if(logger.isDebugEnabled()) {
            logger.debug("writeProperties: "+propertiesName + "--value:"+propertiesValue);
        }
        Properties kyudenProps = new Properties();
        kyudenProps.load(new FileInputStream(System.getProperty("user.dir") + "/config/system/" + "kyuden"));
        PropertyConfigurator.configure(kyudenProps);

        kyudenProps.setProperty(propertiesName, propertiesValue);
        kyudenProps.store(new FileOutputStream(System.getProperty("user.dir") + "/config/system/" + "kyuden"), "kyuden");

        if(logger.isDebugEnabled()) {
            logger.debug("finish");
        }
    }

    /** bytes to hex string **/
    private  String bytesToHexString(byte[] src){
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
//            if (hv.length() < 2) {
//                stringBuilder.append(0);
//            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString().toUpperCase();
    }


    //Unit Test
    public static void main(String[] args) throws IOException, ParseException {
        String flagLogin   = "8888";
        String flagUpdate = "0000";
        String flagRoutine  = "9990";
        KyudenHttpsService client = new KyudenHttpsService();
        //client.request(flagUpdate);
        client.mergerRoutineAndUpdate();

        System.out.println( Integer.parseInt("9") + 1);
    }
}
