package com.anokix.trader.network.dto;

/**
 * data block of GET /api/trader/orders?id= (single order detail).
 * The {@code order} carries the full {@code items[]} list.
 */
public class OrderDetailData {
    public OrdersData.Order order;
}
