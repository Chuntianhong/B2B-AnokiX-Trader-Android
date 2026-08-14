package com.anokix.traderapp.network.dto;

/**
 * Response for the single-ticket endpoints — GET api/common/support/tickets/{id},
 * POST .../tickets, POST .../tickets/reply and POST .../tickets/close. All four
 * return the ticket, with its {@code messages} thread attached.
 */
public class SupportTicketData {

    public SupportOverviewData.Ticket ticket;
}
