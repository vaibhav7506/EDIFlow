import './use-case.css'

const steps = [
  {
    title: 'The business problem',
    explanation: 'Cedar Retail and Northstar Supply use different business systems. They exchange standardized EDI files instead of re-entering orders and invoices by hand. An operations analyst needs to know which documents succeeded and which need attention.',
    input: 'Retailer → X12 850 purchase order → Supplier',
    result: 'An agreed order worth $1,250, ready to validate.',
    script: '“My project connects trading partners. I will show an order, an incorrect invoice, and the evidence explaining the decision.”',
    action: '1. Receive purchase order',
  },
  {
    title: '1. Turn a partner document into an order',
    explanation: 'Cedar orders 10 Widget A units at $75 and 20 Widget B units at $25 from Northstar. EDIFlow validates the order and records PO-REVIEW-01. Find it in Orders below; choose View to inspect its lines and processing history.',
    input: '10 × $75 + 20 × $25 = $1,250',
    result: 'PO-REVIEW-01 → PERSISTED',
    script: '“The Java ingestion service parses X12 into a canonical event. Kafka decouples ingestion from the order service, which validates and stores the result.”',
    action: '2. Check supplier invoice',
  },
  {
    title: '2. Catch an invoice mismatch',
    explanation: 'Northstar sends INV-REVIEW-01 for $1,300 against the $1,250 order. EDIFlow detects the $50 difference and marks the invoice MATCH_EXCEPTION so an operator can investigate. The purchase order remains unchanged.',
    input: '$1,300 invoice − $1,250 purchase order = $50 difference',
    result: 'INV-REVIEW-01 → MATCH_EXCEPTION',
    script: '“The benefit is a visible reconciliation exception. The system records why the invoice failed instead of silently treating it as a successful match.”',
    action: '3. Submit corrected invoice',
  },
  {
    title: '3. Accept the corrected document',
    explanation: 'After review, the supplier sends a new invoice, INV-REVIEW-02, for $1,250. Its total matches the order, so it reaches PERSISTED. The original mismatched invoice remains visible; this simulation does not overwrite it or process a payment.',
    input: '$1,250 invoice = $1,250 purchase order',
    result: 'INV-REVIEW-02 → PERSISTED',
    script: '“This is a new partner document with its own identity. The old exception remains available for investigation, and the corrected document follows the normal validation path.”',
    action: '4. Inspect audit evidence',
  },
  {
    title: '4. Explain every decision',
    explanation: 'The Audit tab below is filtered to this scenario. Inspect the order transitions, the $50 mismatch reason, and the successful corrected invoice. Every displayed decision has an entity, timestamp, and reason.',
    input: 'Order received → Invoice exception → Corrected invoice accepted',
    result: 'A traceable document workflow for an operations analyst.',
    script: '“This public site simulates the workflow in the browser. To prove backend processing, I run the Java/Kafka stack and submit the repository’s EDI fixtures through its secured gateway.”',
    action: '',
  },
]

export function UseCase({ step, onAdvance, onRestart }: { step: number; onAdvance: () => void; onRestart: () => void }) {
  const current = steps[step]
  return <section className="useCase" aria-label="Guided business use case">
    <div className="useCaseHeader">
      <div><p className="eyebrow">PROJECT REVIEW / GUIDED USE CASE</p><h2>A $50 invoice error, caught before it gets lost.</h2></div>
      <span className="scenarioBadge">FICTIONAL SCENARIO</span>
    </div>
    <div className="scenarioActors" aria-label="Business participants">
      <div><small>BUYER</small><strong>Cedar Retail</strong><span>Places the purchase order</span></div>
      <div><small>SUPPLIER</small><strong>Northstar Supply</strong><span>Sends the invoice</span></div>
      <div><small>USER</small><strong>Operations analyst</strong><span>Investigates exceptions</span></div>
    </div>
    <ol className="scenarioProgress" aria-label="Walkthrough progress">
      {['Business problem', 'Purchase order', 'Mismatch', 'Correction', 'Audit trail'].map((label, index) =>
        <li key={label} aria-current={index === step ? 'step' : undefined} className={index < step ? 'complete' : ''}><span>{index < step ? '✓' : index + 1}</span>{label}</li>)}
    </ol>
    <div className="scenarioContent" aria-live="polite">
      <p className="eyebrow">STEP {step + 1} OF {steps.length} · BROWSER SIMULATION</p>
      <h3>{current.title}</h3><p>{current.explanation}</p>
      <dl className="scenarioEvidence"><div><dt>Input / check</dt><dd>{current.input}</dd></div><div><dt>Observable result</dt><dd>{current.result}</dd></div></dl>
    </div>
    <details className="presenterNotes"><summary>What to say in your project review</summary><p>{current.script}</p></details>
    <div className="scenarioActions">
      {current.action ? <button className="uploadButton" onClick={onAdvance}>{current.action}</button> : <p className="reviewComplete">Walkthrough complete. Explore the tabs below or start again.</p>}
      <button className="scenarioReset" onClick={onRestart}>Restart demo</button>
      <a href="#operations-data">See records below ↓</a>
      <a href="https://github.com/vaibhav7506/EDIFlow/blob/main/docs/project-review.md" target="_blank" rel="noreferrer">Full review script ↗</a>
    </div>
    <p className="scenarioDisclaimer">These actions update the sample tables below. They simulate document processing; no live Kafka or database connection is used on this public site.</p>
  </section>
}
