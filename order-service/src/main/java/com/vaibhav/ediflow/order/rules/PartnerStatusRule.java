package com.vaibhav.ediflow.order.rules;
import com.vaibhav.ediflow.order.domain.PartnerStatus; import org.springframework.stereotype.Component;
@Component public class PartnerStatusRule implements BusinessRule { public RuleResult evaluate(RuleContext c){return switch(c.partner().getStatus()){case ACTIVE->RuleResult.pass();case PENDING->RuleResult.hold("Partner is pending approval.");case SUSPENDED->RuleResult.reject("Partner is suspended.");};} public int order(){return 10;} }
