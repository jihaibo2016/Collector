package com.ztequantum.util;

import java.text.ParseException;

/**
 * Created by zhangchao on 2016/8/4.
 */
public class StringUtils {

    /** replace string on special position **/
    public static String replaceStringByPosition(String str,String rstr,int startIndex){
        if(startIndex>str.length()-1){
            throw new ArrayIndexOutOfBoundsException("array out of bounds");
        }
        String preStr = str.substring(0,startIndex);
        String subStr = str.substring(startIndex+rstr.length());
        return  preStr+rstr+subStr;
    }


    //for test only
    public static void main(String[] args) throws ParseException {
        String a = "1234567901223444545666";
        String b = "#8#";
        String c = StringUtils.replaceStringByPosition(a,b,0);
        System.out.println(c);
    }
}
