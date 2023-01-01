package com.sanbing.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sanbing.common.utils.Dingding;
import com.sanbing.common.utils.HttpClientUtil;
import com.sanbing.service.MonitorService;
import com.sun.org.apache.bcel.internal.generic.ALOAD;
import com.taobao.api.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取酷币钱包数据信息
 */

@Slf4j
@Service
public class MonitorServiceImpl implements MonitorService {

    @Scheduled(fixedRate = 60 * 60 * 1000)
    @Override
    public void getInfo() {
        HttpClient httpClient = null;
        httpClient = HttpClients.custom().disableAutomaticRetries().build();
        String url = "https://apilist.tronscanapi.com/api/accountv2";

        Map<String, String> paramBody = new HashMap<>();
        paramBody.put("address", "TUpHuDkiCCmwaTZBHZvQdwWzGNm5t8J2b9");
        String result = null;

        try {
            result = HttpClientUtil.doGet(httpClient, url, null, paramBody);
//            log.info(result);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
        JSONObject data = JSONObject.parseObject(result);
        JSONArray array = data.getJSONArray("withPriceTokens");
        for (int i = 0; i < array.size(); i++) {
            JSONObject json = array.getJSONObject(i);
            String tokenAbbr = json.getString("tokenAbbr");
            String balance = json.getString("balance");
            Integer tokenDecimal = json.getInteger("tokenDecimal");
            if ("USDT".equals(tokenAbbr)) {
                String before = balance.substring(0, balance.length() - tokenDecimal);
                String last = balance.substring(balance.length() - tokenDecimal);
                String str = before + "." + last;
                try {
                    Dingding.sendMessage("热钱包usdt数量：\n" + str);
                } catch (ApiException e) {
                    e.printStackTrace();
                }
                log.info("热钱包usdt数量：" + str);
            }
        }

    }
}
