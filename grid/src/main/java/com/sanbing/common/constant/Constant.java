package com.sanbing.common.constant;

import java.util.HashMap;
import java.util.Map;

/*
常量类
 */
public class Constant {


    //现货常量
    public static final Map<String, String> BINA_SPOT_POSITION_MAP = new HashMap<>();

    public static final Map<String, Double> BINA_SPOT_PRICE_MAP = new HashMap<>();

    public static final String BINA_SPOT_BASE_URL = "https://api.binance.com";
    //常用时间格式
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

}
