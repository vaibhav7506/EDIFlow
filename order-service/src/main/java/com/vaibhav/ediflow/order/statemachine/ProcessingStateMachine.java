package com.vaibhav.ediflow.order.statemachine;

import com.vaibhav.ediflow.order.domain.*;
import com.vaibhav.ediflow.order.repository.ProcessingEventRepository;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ProcessingStateMachine {
    private final ProcessingEventRepository events;
    public ProcessingStateMachine(ProcessingEventRepository events){this.events=events;}
    public void received(PurchaseOrder o){events.save(new ProcessingEvent("PURCHASE_ORDER",o.getId(),null,OrderStatus.RECEIVED.name(),"Canonical event consumed."));}
    public void received(Invoice i){events.save(new ProcessingEvent("INVOICE",i.getId(),null,InvoiceStatus.RECEIVED.name(),"Canonical event consumed."));}
    public void transition(PurchaseOrder o,OrderStatus to,String reason){OrderStatus from=o.getStatus();Map<OrderStatus,Set<OrderStatus>> allowed=Map.of(OrderStatus.RECEIVED,Set.of(OrderStatus.VALIDATING),OrderStatus.VALIDATING,Set.of(OrderStatus.VALIDATED,OrderStatus.REJECTED),OrderStatus.VALIDATED,Set.of(OrderStatus.PERSISTED));if(!allowed.getOrDefault(from,Set.of()).contains(to))throw new IllegalStateException("Invalid order transition "+from+" -> "+to);o.status(to);events.save(new ProcessingEvent("PURCHASE_ORDER",o.getId(),from.name(),to.name(),reason));}
    public void transition(Invoice i,InvoiceStatus to,String reason){InvoiceStatus from=i.getStatus();Map<InvoiceStatus,Set<InvoiceStatus>> allowed=Map.of(InvoiceStatus.RECEIVED,Set.of(InvoiceStatus.VALIDATING),InvoiceStatus.VALIDATING,Set.of(InvoiceStatus.VALIDATED,InvoiceStatus.REJECTED,InvoiceStatus.MATCH_EXCEPTION),InvoiceStatus.VALIDATED,Set.of(InvoiceStatus.PERSISTED));if(!allowed.getOrDefault(from,Set.of()).contains(to))throw new IllegalStateException("Invalid invoice transition "+from+" -> "+to);i.status(to);events.save(new ProcessingEvent("INVOICE",i.getId(),from.name(),to.name(),reason));}
}
