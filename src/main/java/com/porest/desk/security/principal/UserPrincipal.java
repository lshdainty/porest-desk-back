package com.porest.desk.security.principal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPrincipal {
    private final Long rowId;
    private final String userId;
    private final String userName;
    private final String userEmail;
}
