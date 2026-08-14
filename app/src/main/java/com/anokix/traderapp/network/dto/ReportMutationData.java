package com.anokix.traderapp.network.dto;

/**
 * Response for POST api/trader/reports/generate and POST api/trader/reports/delete.
 * Both return the refreshed {@link ReportsData.Summary}; generate additionally returns
 * the newly created {@link ReportsData.Report}.
 */
public class ReportMutationData {

    /** Present on generate, absent on delete. */
    public ReportsData.Report report;
    public ReportsData.Summary summary;
}
