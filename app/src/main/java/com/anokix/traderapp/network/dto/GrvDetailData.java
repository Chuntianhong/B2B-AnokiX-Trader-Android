package com.anokix.traderapp.network.dto;

/**
 * Response for GET api/trader/grvs/{id} and POST api/trader/grvs/{id}/confirm —
 * both wrap a single {@link GrvListData.Grv} under "grv".
 */
public class GrvDetailData {
    public GrvListData.Grv grv;
}
