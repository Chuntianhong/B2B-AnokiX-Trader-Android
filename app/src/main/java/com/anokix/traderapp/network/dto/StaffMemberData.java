package com.anokix.traderapp.network.dto;

/**
 * Response for the single-member staff endpoints — POST api/trader/staff (add),
 * POST api/trader/staff/password and POST api/trader/staff/update. All three
 * return the member in its new state.
 */
public class StaffMemberData {

    public StaffData.Member staff;
}
