/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mohamnag.inappbilling;

import com.mohamnag.inappbilling.helper.IabResult;
import org.json.JSONObject;

/**
 * A helper to build up JSONObjects with proper structure to be returned to
 * error callback.
 *
 * @author mohamang
 */
public class ErrorEvent {
    private static final String errorCodeKey = "errorCode";
    private static final String msgKey = "msg";
    private static final String nativeEventKey = "nativeEvent";
    
    public static JSONObject buildJson(int errorCode, String msg, IabResult result) {
        JSONObject ret = new JSONObject();
        ret.append(errorCodeKey, errorCode);
        ret.append(msgKey, msg);
        
        if(result != null) {
            ret.append(nativeEventKey, result.toJson());
        }
        
        return ret;
    }
}
