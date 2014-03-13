package com.airbnb.billow;

import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.identitymanagement.model.User;
import com.google.common.collect.ImmutableList;
import lombok.Data;

@Data
public class IAMUserWithKeys {
    private final User user;
    private final ImmutableList<AccessKeyMetadata> keys;
}
