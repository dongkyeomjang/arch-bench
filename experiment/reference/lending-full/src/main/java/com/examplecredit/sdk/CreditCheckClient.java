package com.examplecredit.sdk;

import java.time.Instant;
import java.util.List;

public class CreditCheckClient {
    /** POST https://credit.example.com/v2/check — 현재는 스텁 */
    public CreditCheckResponse check(String memberId, String memberName) {
        CreditCheckResponse r = new CreditCheckResponse();
        r.approved = memberName == null || !memberName.startsWith("BLOCKED");
        r.score = r.approved ? 742 : 410;
        r.reasonCodes = List.of(r.approved ? "OK" : "WATCHLIST");
        r.checkedAt = Instant.now();
        r.provider = new CreditCheckResponse.Provider();
        r.provider.name = "ExampleCredit";
        r.provider.version = "2.3";
        return r;
    }
}
