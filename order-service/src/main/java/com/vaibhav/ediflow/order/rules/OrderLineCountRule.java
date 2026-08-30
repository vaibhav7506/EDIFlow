package com.vaibhav.ediflow.order.rules;

import com.vaibhav.ediflow.events.DocumentType;
import org.springframework.stereotype.Component;

@Component
public class OrderLineCountRule implements BusinessRule {
    @Override
    public RuleResult evaluate(RuleContext context) {
        if (context.event().documentType() != DocumentType.PO_850
                || context.event().payload().declaredLineCount() == null) {
            return RuleResult.pass();
        }

        int declared = context.event().payload().declaredLineCount();
        int parsed = context.event().payload().lines().size();
        return declared == parsed
                ? RuleResult.pass()
                : RuleResult.reject("LINE_COUNT_MISMATCH: CTT01 declares " + declared
                        + " line items but " + parsed + " PO1 segments were parsed.");
    }

    @Override
    public int order() {
        return 30;
    }
}
