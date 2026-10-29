import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const emptyPage = { content: [], totalElements: 0 }
const order = { id: 'order-1', partnerCode: 'SENDERID', poNumber: 'PO-1001', poDate: '2026-08-29', status: 'PERSISTED', totalAmount: 25, createdAt: '2026-08-29T00:00:00Z' }
const deadLetter = { id: 'dlq-1', sourceTopic: 'edi.documents', partnerCode: 'SENDERID', errorReason: 'Malformed BEG segment', retryCount: 0, status: 'OPEN', createdAt: '2026-08-29T00:00:00Z' }

async function mockBackend(page: Page, options: { orders?: boolean; deadLetters?: boolean } = {}) {
  const writes: string[] = []
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url())
    if (url.pathname === '/oauth2/token') return route.fulfill({ json: { access_token: 'test-token', scope: 'edi:read edi:write admin' } })
    if (url.pathname === '/api/v1/edi/upload' && request.method() === 'POST') { writes.push(url.pathname); return route.fulfill({ status: 202, json: { eventId: 'event-1', documentType: 'PO_850', status: 'QUEUED' } }) }
    if (url.pathname === '/api/v1/dead-letters/dlq-1/retry' && request.method() === 'POST') { writes.push(url.pathname); return route.fulfill({ json: { ...deadLetter, status: 'RETRIED', retryCount: 1 } }) }
    if (url.pathname === '/api/v1/orders') return route.fulfill({ json: options.orders ? { content: [order], totalElements: 1 } : emptyPage })
    if (url.pathname === '/api/v1/invoices') return route.fulfill({ json: emptyPage })
    if (url.pathname === '/api/v1/dead-letters') return route.fulfill({ json: options.deadLetters ? { content: [deadLetter], totalElements: 1 } : emptyPage })
    if (url.pathname === '/api/v1/partners') return route.fulfill({ json: [] })
    if (url.pathname === '/api/v1/metrics/summary') return route.fulfill({ json: { kafkaLag: 3, deadLetterDepth: options.deadLetters ? 1 : 0 } })
    if (url.pathname === '/api/v1/audit') return route.fulfill({ json: emptyPage })
    return route.continue()
  })
  return writes
}

async function login(page: Page) {
  await page.goto('http://127.0.0.1:43174/')
  await page.getByLabel('Client ID').fill('admin-client')
  await page.getByLabel('Client secret').fill('secret')
  await page.getByRole('button', { name: 'Request access token' }).click()
  await expect(page.getByTestId('pipeline')).toBeVisible()
}

test('uploads an EDI document and animates the real pipeline event', async ({ page }) => {
  const writes = await mockBackend(page, { orders: true })
  await login(page)
  await page.getByLabel('Upload EDI file').setInputFiles({ name: 'order.edi', mimeType: 'text/plain', buffer: Buffer.from('ISA*00*') })
  await expect.poll(() => writes.filter(url => url.includes('/edi/upload')).length).toBe(1)
  await expect(page.locator('.packet.active')).toBeAttached()
  await expect(page.getByText('PO-1001')).toBeVisible()
})

test('retries a dead-letter record through the admin control', async ({ page }) => {
  const writes = await mockBackend(page, { deadLetters: true })
  await login(page)
  await page.getByRole('button', { name: 'dead letters' }).click()
  await expect(page.getByTestId('dead-letter-table')).toContainText('Malformed BEG segment')
  await page.getByRole('button', { name: 'Retry' }).click()
  await expect.poll(() => writes.filter(url => url.includes('/retry')).length).toBe(1)
})

test('login, upload form, and orders table have no serious WCAG violations', async ({ page }) => {
  await mockBackend(page, { orders: true })
  await page.goto('http://127.0.0.1:43174/')
  let results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()
  expect(results.violations.filter(v => ['serious', 'critical'].includes(v.impact ?? ''))).toEqual([])
  await page.getByLabel('Client ID').fill('admin-client')
  await page.getByLabel('Client secret').fill('secret')
  await page.getByRole('button', { name: 'Request access token' }).click()
  await expect(page.getByText('PO-1001')).toBeVisible()
  results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()
  expect(results.violations.filter(v => ['serious', 'critical'].includes(v.impact ?? ''))).toEqual([])
})
