package com.sanbing.service.bina.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.binance.connector.client.exceptions.BinanceClientException;
import com.binance.connector.client.exceptions.BinanceConnectorException;
import com.binance.connector.client.impl.SpotClientImpl;
import com.binance.connector.client.impl.WebsocketClientImpl;
import com.sanbing.common.constant.Constant;
import com.sanbing.service.bina.GridService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;

/*
 * 网格交易*/
@Slf4j
@Service
public class GridServiceImpl implements GridService {

    @Value("${bina_key}")
    private String bina_key;
    @Value("${bina_secret}")
    private String bina_secret;

    double price = 0;

    @Scheduled(fixedRate = 300)
    @Override
    public void GridTransaction() {
        long startTime = System.currentTimeMillis();
        //获取现货仓位
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        SpotClientImpl client = new SpotClientImpl(bina_key, bina_secret);
        String result = client.createWallet().getUserAsset(parameters);
        JSONArray array = JSONArray.parseArray(result);

        double position_usdt_free = 0;
        double position_usdt_locked = 0;
        double position_busd_free = 0;
        double position_busd_locked = 0;
        for (int i = 0; i < array.size(); i++) {
            JSONObject object = array.getJSONObject(i);
            if ("USDT".equals(object.getString("asset"))) {
                position_usdt_free = object.getDouble("free");
                position_usdt_locked = object.getDouble("locked");
            }
            if ("BUSD".equals(object.getString("asset"))) {
                position_busd_free = object.getDouble("free");
                position_busd_locked = object.getDouble("locked");
            }

        }

        //仓位为usdt(未锁定)
        if (position_usdt_free > 1) {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            price = Constant.BINA_SPOT_PRICE_MAP.get("buy_price");
            Double quantity = (position_usdt_free/price);
            params.put("side", "BUY");
            params.put("quantity", Double.valueOf(quantity.intValue()));
            params.put("price", price);
            if(position_busd_locked<1){
                newOrder(params);
            }


        }
        //仓位为usdt(锁定)
        if (position_usdt_locked > 1) {
            double current_price = Constant.BINA_SPOT_PRICE_MAP.get("buy_price");
            double buy_num = Constant.BINA_SPOT_PRICE_MAP.get("buy_num");
            if (current_price > price && buy_num > 10 * 1000000) {
                Double quantity = (position_usdt_locked/current_price);
                cancelOrder();
                LinkedHashMap<String, Object> params = new LinkedHashMap<>();
                params.put("side", "BUY");
                params.put("quantity", Double.valueOf(quantity.intValue()));
                params.put("price", current_price);
                newOrder(params);
            }
        }
        //仓位为busd(未锁定)
        if (position_busd_free > 1) {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            price = Constant.BINA_SPOT_PRICE_MAP.get("sell_price");
            Double quantity = (position_busd_free/price);
            params.put("side", "SELL");
            params.put("quantity", Double.valueOf(quantity.intValue()));
            params.put("price", price);
            if(position_usdt_locked<1){
                newOrder(params);
            }

        }
        //仓位为busd(锁定)
        if (position_busd_locked > 1) {
            double current_price = Constant.BINA_SPOT_PRICE_MAP.get("sell_price");
            double sell_num = Constant.BINA_SPOT_PRICE_MAP.get("sell_num");
            if (current_price < price && sell_num > 10 * 1000000) {
                cancelOrder();
                Double quantity = (position_busd_locked/current_price);
                LinkedHashMap<String, Object> params = new LinkedHashMap<>();
                params.put("side", "SELL");
                params.put("quantity", Double.valueOf(quantity.intValue()));
                params.put("price", current_price);
                newOrder(params);
            }
        }
        long endTime = System.currentTimeMillis();
//        log.info(""+(endTime-startTime));
    }

    @PostConstruct
    @Override
    public void getMarketData() {
        WebsocketClientImpl client = new WebsocketClientImpl();
        client.bookTicker("busdusdt", ((event) -> {
//            System.out.println(event);
            JSONObject object = JSONObject.parseObject(event);
            Constant.BINA_SPOT_PRICE_MAP.put("buy_price", object.getDoubleValue("b"));
            Constant.BINA_SPOT_PRICE_MAP.put("buy_num", object.getDoubleValue("B"));
            Constant.BINA_SPOT_PRICE_MAP.put("sell_price", object.getDoubleValue("a"));
            Constant.BINA_SPOT_PRICE_MAP.put("sell_num", object.getDoubleValue("A"));

        }));
    }

    //下单
    public void newOrder(LinkedHashMap<String, Object> parameters) {
        SpotClientImpl client2 = new SpotClientImpl(bina_key, bina_secret, Constant.BINA_SPOT_BASE_URL);

        parameters.put("symbol", "BUSDUSDT");
        parameters.put("type", "LIMIT");
        parameters.put("timeInForce", "GTC");

        try {
            String result = client2.createTrade().newOrder(parameters);
            log.info(result);
        } catch (BinanceConnectorException e) {
            log.error("fullErrMessage: {}", e.getMessage(), e);
        } catch (BinanceClientException e) {
            log.error("fullErrMessage: {} \nerrMessage: {} \nerrCode: {} \nHTTPStatusCode: {}",
                    e.getMessage(), e.getErrMsg(), e.getErrorCode(), e.getHttpStatusCode(), e);
        }
    }

    //撤单
    public void cancelOrder(){
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();

        SpotClientImpl client = new SpotClientImpl(bina_key, bina_secret, Constant.BINA_SPOT_BASE_URL);

        parameters.put("symbol", "BUSDUSDT");

        try {
            String result = client.createTrade().cancelOpenOrders(parameters);
            log.info(result);
        } catch (BinanceConnectorException e) {
            log.error("fullErrMessage: {}", e.getMessage(), e);
        } catch (BinanceClientException e) {
            log.error("fullErrMessage: {} \nerrMessage: {} \nerrCode: {} \nHTTPStatusCode: {}",
                    e.getMessage(), e.getErrMsg(), e.getErrorCode(), e.getHttpStatusCode(), e);
        }
    }
}
