package com.vaibhav.ediflow.order.statemachine;

import com.vaibhav.ediflow.order.domain.*;
import com.vaibhav.ediflow.order.repository.ProcessingEventRepository;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessingStateMachineTest {
    private final ProcessingEventRepository events = mock(ProcessingEventRepository.class);
    private final ProcessingStateMachine machine = new ProcessingStateMachine(events);

    @Test void orderValidTransitionChangesStateAndAudits() {
        PurchaseOrder order = mock(PurchaseOrder.class);
        when(order.getId()).thenReturn(UUID.randomUUID());
        when(order.getStatus()).thenReturn(OrderStatus.RECEIVED);
        machine.transition(order, OrderStatus.VALIDATING, "validate");
        verify(order).status(OrderStatus.VALIDATING);
        verify(events).save(argThat(event -> event.getFromState().equals("RECEIVED") && event.getToState().equals("VALIDATING")));
    }

    @Test void orderInvalidTransitionIsRejectedWithoutAudit() {
        PurchaseOrder order = mock(PurchaseOrder.class);
        when(order.getStatus()).thenReturn(OrderStatus.RECEIVED);
        assertThrows(IllegalStateException.class, () -> machine.transition(order, OrderStatus.PERSISTED, "skip"));
        verifyNoInteractions(events);
    }

    @Test void invoiceMatchExceptionIsAValidTerminalTransition() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(UUID.randomUUID());
        when(invoice.getStatus()).thenReturn(InvoiceStatus.VALIDATING);
        machine.transition(invoice, InvoiceStatus.MATCH_EXCEPTION, "mismatch");
        verify(invoice).status(InvoiceStatus.MATCH_EXCEPTION);
        verify(events).save(any(ProcessingEvent.class));
    }

    @Test void invoiceCannotLeaveMatchException() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getStatus()).thenReturn(InvoiceStatus.MATCH_EXCEPTION);
        assertThrows(IllegalStateException.class, () -> machine.transition(invoice, InvoiceStatus.PERSISTED, "invalid"));
        verifyNoInteractions(events);
    }
}
