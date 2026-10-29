import type { AuditEvent, DeadLetter, Detail, Invoice, Partner } from './main'

type Demo = {
  orders: Detail[]
  invoices: Invoice[]
  deadLetters: DeadLetter[]
  partners: Partner[]
  audit: AuditEvent[]
}

const timestamp = '2026-10-01T09:00:00Z'

function sampleOrder(sequence: number, status = 'PERSISTED'): Detail {
  return {
    id: `demo-order-${sequence}`, partnerCode: 'NORTHSTAR',
    poNumber: `PO-${1000 + sequence}`, poDate: '2026-10-01', status,
    totalAmount: 1250, createdAt: timestamp,
    lines: [
      { lineNumber: 1, sku: 'WIDGET-A', quantity: 10, unitOfMeasure: 'EA', unitPrice: 75, extendedPrice: 750 },
      { lineNumber: 2, sku: 'WIDGET-B', quantity: 20, unitOfMeasure: 'EA', unitPrice: 25, extendedPrice: 500 },
    ],
    history: [
      { toState: 'QUEUED', reason: 'Sample X12 850 accepted', occurredAt: timestamp },
      { fromState: 'QUEUED', toState: status, reason: 'Simulated validation and business-rule evaluation', occurredAt: timestamp },
    ],
  }
}

export function createDemo(): Demo {
  const orders = [sampleOrder(1), sampleOrder(2, 'MATCH_EXCEPTION')]
  return {
    orders,
    invoices: [
      { id: 'demo-invoice-1', invoiceNumber: 'INV-2001', referencedPoNumber: 'PO-1001', status: 'PERSISTED', totalAmount: 1250, createdAt: timestamp },
      { id: 'demo-invoice-2', invoiceNumber: 'INV-2002', referencedPoNumber: 'PO-1002', status: 'MATCH_EXCEPTION', totalAmount: 1300, createdAt: timestamp },
    ],
    deadLetters: [{ id: 'demo-dlq-1', sourceTopic: 'edi.documents', partnerCode: 'NORTHSTAR', errorReason: 'Sample transient consumer failure — retry to recover', retryCount: 0, status: 'OPEN', createdAt: timestamp }],
    partners: [
      { id: 'demo-partner-1', code: 'NORTHSTAR', name: 'Northstar Supply (sample)', status: 'APPROVED' },
      { id: 'demo-partner-2', code: 'HARBOR', name: 'Harbor Trading (sample)', status: 'PENDING' },
    ],
    audit: orders.map(order => ({ id: `audit-${order.id}`, entityType: 'ORDER', entityId: order.id, fromState: 'QUEUED', toState: order.status, reason: 'Seeded demonstration record', occurredAt: timestamp })),
  }
}

export function simulateDocument(current: Demo): Demo {
  const sequence = current.orders.length + 1
  const order = sampleOrder(sequence)
  return {
    ...current,
    orders: [order, ...current.orders],
    invoices: [{ id: `demo-invoice-${sequence}`, invoiceNumber: `INV-${2000 + sequence}`, referencedPoNumber: order.poNumber, status: 'PERSISTED', totalAmount: order.totalAmount, createdAt: timestamp }, ...current.invoices],
    audit: [
      { id: `audit-${order.id}`, entityType: 'ORDER', entityId: order.id, fromState: 'QUEUED', toState: 'PERSISTED', reason: 'Sample order validated; matching invoice reconciled (simulation)', occurredAt: new Date().toISOString() },
      ...current.audit,
    ],
  }
}

export function retryDemo(current: Demo, id: string): Demo {
  const record = current.deadLetters.find(item => item.id === id)
  if (!record || record.status !== 'OPEN') return current
  return {
    ...current,
    deadLetters: current.deadLetters.map(item => item.id === id ? { ...item, status: 'RETRIED', retryCount: item.retryCount + 1 } : item),
    audit: [{ id: `audit-retry-${id}`, entityType: 'DEAD_LETTER', entityId: id, fromState: 'OPEN', toState: 'RETRIED', reason: 'Simulated recovery after a transient consumer failure', occurredAt: new Date().toISOString() }, ...current.audit],
  }
}

export function approveDemo(current: Demo, id: string): Demo {
  const partner = current.partners.find(item => item.id === id)
  if (!partner || partner.status !== 'PENDING') return current
  return {
    ...current,
    partners: current.partners.map(item => item.id === id ? { ...item, status: 'APPROVED' } : item),
    audit: [{ id: `audit-approve-${id}`, entityType: 'PARTNER', entityId: id, fromState: 'PENDING', toState: 'APPROVED', reason: 'Partner approved in the demo workspace', occurredAt: new Date().toISOString() }, ...current.audit],
  }
}
