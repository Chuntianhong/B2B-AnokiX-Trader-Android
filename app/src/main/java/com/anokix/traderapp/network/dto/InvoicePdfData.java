package com.anokix.traderapp.network.dto;

/**
 * Response for GET api/trader/invoices/{id}/pdf — {@code data} is a base64-encoded
 * PDF that the client decodes to a file and opens/shares.
 */
public class InvoicePdfData {
    public String filename;
    public String mime;
    /** Base64-encoded PDF bytes. */
    public String data;
}
