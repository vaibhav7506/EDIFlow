import { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

type Order = { id: string; partnerId: string; partnerCode: string; poNumber: string; poDate: string; status: string; totalAmount: number; createdAt: string }
type Detail = Order & { lines: { lineNumber: number; sku: string; quantity: number; unitOfMeasure: string; unitPrice: number; extendedPrice: number }[] }
type Page = { content: Order[]; totalElements: number }
const money = (amount: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount)

function App() {
  const [orders, setOrders] = useState<Order[]>([]); const [error, setError] = useState(''); const [uploading, setUploading] = useState(false)
  const [selected, setSelected] = useState<Detail | null>(null); const [query, setQuery] = useState('')
  const load = async () => { try { const r = await fetch(`/api/v1/orders?size=50${query ? `&status=${query}` : ''}`); if (!r.ok) throw new Error('Could not load orders. Is the API running?'); setOrders((await r.json() as Page).content); } catch (e) { setError(e instanceof Error ? e.message : 'Could not load orders.') } }
  useEffect(() => { void load() }, [query])
  const upload = async (file: File) => { setUploading(true); setError(''); const data = new FormData(); data.append('file', file); try { const r = await fetch('/api/v1/edi/upload', { method: 'POST', body: data }); if (!r.ok) { const body = await r.json() as { error?: string }; throw new Error(body.error ?? 'Upload failed.'); } await load() } catch (e) { setError(e instanceof Error ? e.message : 'Upload failed.') } finally { setUploading(false) } }
  const open = async (id: string) => { const r = await fetch(`/api/v1/orders/${id}`); if (r.ok) setSelected(await r.json() as Detail) }
  return <main><header><div><p className="eyebrow">OPERATIONS CONSOLE / PHASE 1</p><h1>EDI<span>Flow</span></h1></div><div className="status"><i/> SYSTEM ONLINE</div></header>
    <section className="upload"><div><p className="eyebrow">X12 850 INGESTION</p><h2>Drop a purchase order</h2><p>ISA-defined delimiters are interpreted server-side, then persisted as an operational order.</p></div><label className="uploadButton">{uploading ? 'Processing…' : 'Select EDI file'}<input aria-label="Upload EDI file" disabled={uploading} type="file" accept=".edi,.x12,.txt" onChange={e => { const file = e.target.files?.[0]; if (file) void upload(file); e.currentTarget.value = '' }} /></label></section>
    {error && <p role="alert" className="error">{error}</p>}
    <section className="orders"><div className="tableHead"><div><p className="eyebrow">PERSISTED ORDERS</p><h2>{orders.length} visible orders</h2></div><select aria-label="Filter orders by status" value={query} onChange={e => setQuery(e.target.value)}><option value="">All statuses</option><option value="PERSISTED">Persisted</option><option value="RECEIVED">Received</option></select></div>
      <div className="tableWrap"><table><thead><tr><th>PO number</th><th>Partner</th><th>Date</th><th>Status</th><th>Total</th><th></th></tr></thead><tbody>{orders.length === 0 ? <tr><td colSpan={6} className="empty">No orders yet. Upload the included valid-850.edi fixture to begin.</td></tr> : orders.map(o => <tr key={o.id}><td className="mono">{o.poNumber}</td><td>{o.partnerCode}</td><td>{o.poDate}</td><td><span className="pill">{o.status}</span></td><td>{money(o.totalAmount)}</td><td><button onClick={() => void open(o.id)}>View</button></td></tr>)}</tbody></table></div></section>
    {selected && <div className="overlay" role="dialog" aria-modal="true" aria-label="Order detail"><article><button className="close" onClick={() => setSelected(null)}>×</button><p className="eyebrow">ORDER DETAIL</p><h2>{selected.poNumber}</h2><p>{selected.partnerCode} · {selected.poDate} · <span className="pill">{selected.status}</span></p><table><thead><tr><th>Line</th><th>SKU</th><th>Qty</th><th>Unit price</th><th>Extended</th></tr></thead><tbody>{selected.lines.map(l => <tr key={l.lineNumber}><td>{l.lineNumber}</td><td className="mono">{l.sku}</td><td>{l.quantity} {l.unitOfMeasure}</td><td>{money(l.unitPrice)}</td><td>{money(l.extendedPrice)}</td></tr>)}</tbody></table><h3>Total {money(selected.totalAmount)}</h3></article></div>}
  </main>
}
createRoot(document.getElementById('root')!).render(<App />)
