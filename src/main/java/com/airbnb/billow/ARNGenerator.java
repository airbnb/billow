package com.airbnb.billow;


import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.rds.model.DBInstance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ARNGenerator {
    private final String accountNumber;

    ARNGenerator(final AmazonIdentityManagementClient iamClient) {
        this.accountNumber = loadAccountNumber(iamClient);
    }

    public String rdsARN(String regionName, DBInstance instance) {
        return String.format(
                "arn:aws:rds:%s:%s:db:%s",
                regionName,
                accountNumber,
                instance.getDBInstanceIdentifier()
        );
    }

    private static String loadAccountNumber(AmazonIdentityManagementClient iamClient) {

        /*
         * Unfortunately this appears to be the only way to get an account number
         */
        String userARN = null;

        try {
            GetUserResult getUserResult = iamClient.getUser();
            userARN = getUserResult.getUser().getArn();
        } catch(AmazonServiceException e) {
            // Access Denied
            if(e.getStatusCode() == 403) {
                /*
                 * Bizarrely, the only information we want ends up being
                 * revealed in the error message as well, so we can just
                 * parse that.
                 */
                userARN = extractUserARNFromError(e.getMessage());
            }

            if(userARN == null) {
                throw e;
            }
        }

        return userARN.split(":")[4];
    }

    private static String extractUserARNFromError(String errorMessage) {
        Pattern p = Pattern.compile("arn:aws:iam::\\d+:user");
        Matcher m = p.matcher(errorMessage);

        if (m.find()) {
            return m.group();
        }

        return null;
    }
}
