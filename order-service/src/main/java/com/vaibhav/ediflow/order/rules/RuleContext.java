package com.vaibhav.ediflow.order.rules;
import com.vaibhav.ediflow.events.CanonicalEdiEvent; import com.vaibhav.ediflow.order.domain.*;
public record RuleContext(CanonicalEdiEvent event,Partner partner,PurchaseOrder referencedOrder){}
