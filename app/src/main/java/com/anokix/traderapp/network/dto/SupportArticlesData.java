package com.anokix.traderapp.network.dto;

import java.util.List;

/** Response for GET api/common/support/articles/search?q= — help-article search results. */
public class SupportArticlesData {

    public String query;
    public List<SupportOverviewData.Article> articles;
    public int total;
}
