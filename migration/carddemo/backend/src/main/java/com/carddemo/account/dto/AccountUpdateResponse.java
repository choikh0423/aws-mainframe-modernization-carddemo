package com.carddemo.account.dto;

/**
 * The CACTUPA screen after a turn: the state, the info line (INFOMSG), the error line (ERRMSG)
 * and the field values to redisplay.
 */
public class AccountUpdateResponse {

    public AccountUpdateState state;
    public String infoMessage;
    public String errorMessage;
    public AccountFields details;

    public static AccountUpdateResponse of(AccountUpdateState state, String infoMessage,
                                           String errorMessage, AccountFields details) {
        AccountUpdateResponse response = new AccountUpdateResponse();
        response.state = state;
        response.infoMessage = infoMessage;
        response.errorMessage = errorMessage;
        response.details = details;
        return response;
    }
}
