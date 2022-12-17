package com.sanbing.service.impl;

import com.huobi.client.AccountClient;
import com.huobi.client.MarketClient;
import com.huobi.client.TradeClient;
import com.huobi.client.req.account.AccountBalanceRequest;
import com.huobi.client.req.market.MarketDetailMergedRequest;
import com.huobi.client.req.market.SubMarketBBORequest;
import com.huobi.client.req.trade.BatchCancelOpenOrdersRequest;
import com.huobi.client.req.trade.CreateOrderRequest;
import com.huobi.client.req.trade.OpenOrdersRequest;
import com.huobi.constant.HuobiOptions;
import com.huobi.model.account.Account;
import com.huobi.model.account.AccountBalance;
import com.huobi.model.account.Balance;
import com.huobi.model.market.MarketDetailMerged;
import com.huobi.model.trade.BatchCancelOpenOrdersResult;
import com.huobi.model.trade.Order;
import com.sanbing.service.GridHuobiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/*
 * 网格交易*/
@Slf4j
@Service
public class GridHuobiServiceImpl implements GridHuobiService {

    @Value("${huobi_key}")
    private String huobi_key;
    @Value("${huobi_secret}")
    private String huobi_secret;

    @Scheduled(fixedRate = 600)
    @Override
    public void GridTransaction() {


        Long accountId = getAccountId();
        String symbol = "usdcusdt";
        MarketClient marketClient = MarketClient.create(new HuobiOptions());
        MarketDetailMerged marketDetailMerged = marketClient.getMarketDetailMerged(MarketDetailMergedRequest.builder().symbol(symbol).build());
        BigDecimal askPrice = marketDetailMerged.getAsk().getPrice();
        BigDecimal bidPrice = marketDetailMerged.getBid().getPrice();
        double sell_num = marketDetailMerged.getAsk().getAmount().doubleValue();
        double buy_num = marketDetailMerged.getBid().getAmount().doubleValue();

        BigDecimal position_usdt_trade = new BigDecimal(0);
        BigDecimal position_usdt_frozen = new BigDecimal(0);
        BigDecimal position_usdc_trade = new BigDecimal(0);
        BigDecimal position_usdc_frozen = new BigDecimal(0);
        //获取现货仓位
        try {
            List<Balance> balanceList = getAccountBalance(accountId);
            for (int i = 0; i < balanceList.size(); i++) {
                Balance balance = balanceList.get(i);
                if ("usdt".equals(balance.getCurrency())) {
                    if ("trade".equals(balance.getType())) {
                        position_usdt_trade = balance.getBalance();
                    } else {
                        position_usdt_frozen = balance.getBalance();
                    }
                }
                if ("usdc".equals(balance.getCurrency())) {
                    if ("trade".equals(balance.getType())) {
                        position_usdc_trade = balance.getBalance();
                    } else {
                        position_usdc_frozen = balance.getBalance();
                    }
                }
            }
            //仓位为usdt(未锁定)
            if (position_usdt_trade.doubleValue() > 1 && position_usdc_frozen.doubleValue() < 1) {
                String amount = position_usdt_trade.divide(bidPrice, 1, RoundingMode.DOWN).toString();
                buyOrder(accountId, amount, bidPrice);
            }
            //仓位为usdc(未锁定)
            if (position_usdc_trade.doubleValue() > 1 && position_usdt_frozen.doubleValue() < 1) {
                sellOrder(accountId, position_usdc_trade.toString(), askPrice);
            }
            //仓位为usdt(锁定)
            if (position_usdt_frozen.doubleValue() > 1) {
                double price = getOrderPrice(accountId);
                String amount = position_usdt_frozen.divide(bidPrice, 1, RoundingMode.DOWN).toString();
                if (bidPrice.doubleValue() > price && buy_num > 100000) {
                    batchCancelOpenOrders(accountId);
                    buyOrder(accountId, amount, bidPrice);
                }
            }
            //仓位为usdc(锁定)
            if (position_usdc_frozen.doubleValue() > 1) {
                double price = getOrderPrice(accountId);
                if (askPrice.doubleValue() < price && sell_num < 100000) {
                    batchCancelOpenOrders(accountId);
                    sellOrder(accountId, position_usdc_frozen.toString(), askPrice);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public void getMarketData() {
        MarketClient marketClient = MarketClient.create(new HuobiOptions());
        String symbol = "usdcusdt";

        marketClient.subMarketBBO(SubMarketBBORequest.builder().symbol(symbol).build(), (marketBBOEvent) -> {
            System.out.println(marketBBOEvent.toString());
        });
    }

    public Long getAccountId() {
        Long accountId = 0L;
        AccountClient accountService = AccountClient.create(HuobiOptions.builder()
                .apiKey(huobi_key)
                .secretKey(huobi_secret)
                .build());

        List<Account> accountList = accountService.getAccounts();
        accountId = accountList.get(0).getId();
        return accountId;
    }

    public void batchCancelOpenOrders(Long spotAccountId) {
        TradeClient tradeService = TradeClient.create(HuobiOptions.builder()
                .apiKey(huobi_key)
                .secretKey(huobi_secret)
                .build());
        BatchCancelOpenOrdersResult result = tradeService.batchCancelOpenOrders(BatchCancelOpenOrdersRequest.builder()
                .accountId(spotAccountId)
                .build());
//        System.out.println("spot batchCancel open Orders:" + result.toString());
    }

    public void sellOrder(Long spotAccountId, String amount, BigDecimal askPrice) {
        String symbol = "usdcusdt";

        TradeClient tradeService = TradeClient.create(HuobiOptions.builder()
                .apiKey(huobi_key)
                .secretKey(huobi_secret)
                .build());

        CreateOrderRequest sellLimitRequest = CreateOrderRequest.spotSellLimit(spotAccountId, symbol, askPrice, new BigDecimal(amount));
        Long sellLimitId = tradeService.createOrder(sellLimitRequest);
//        System.out.println("create sell-limit order:" + sellLimitId);
    }

    public void buyOrder(Long spotAccountId, String amount, BigDecimal bidPrice) {
        String symbol = "usdcusdt";

        TradeClient tradeService = TradeClient.create(HuobiOptions.builder()
                .apiKey(huobi_key)
                .secretKey(huobi_secret)
                .build());
        String clientOrderId = "T" + System.nanoTime();

        CreateOrderRequest buyLimitRequest = CreateOrderRequest.spotBuyLimit(spotAccountId, clientOrderId, symbol, bidPrice, new BigDecimal(amount));
        Long buyLimitId = tradeService.createOrder(buyLimitRequest);
//        System.out.println("create buy-limit order:" + buyLimitId);
    }

    public List<Balance> getAccountBalance(Long accountId) {
        AccountClient accountService = AccountClient.create(HuobiOptions.builder()
                .apiKey(huobi_key)
                .secretKey(huobi_secret)
                .build());

        AccountBalance accountBalance = accountService.getAccountBalance(AccountBalanceRequest.builder()
                .accountId(accountId)
                .build());

        return accountBalance.getList();

    }

    public double getOrderPrice(Long accountId) {
        double price = 0;
        String symbol = "usdcusdt";
        TradeClient tradeService = TradeClient.create(HuobiOptions.builder()
                .apiKey(huobi_key)
                .secretKey(huobi_secret)
                .build());
        List<Order> orderList = tradeService.getOpenOrders(OpenOrdersRequest.builder()
                .accountId(accountId)
                .symbol(symbol)
                .build());
//        System.out.println(orderList);
        if (orderList.size() > 0) {
            Order order = orderList.get(0);
            if ("usdcusdt".equals(order.getSymbol())) {
                price = order.getPrice().setScale(4, RoundingMode.DOWN).doubleValue();
            }

        }
        return price;

    }
}

