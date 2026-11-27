import test from 'node:test'
import assert from 'node:assert/strict'
import { completionChecks } from './completionChecks.js'

const eligible = () => ({
  clinical: { diagnoses: [{ diagnosisType: 'FINAL' }] },
  billing: { invoices: [{ invoice: { status: 'ISSUED', lines: [{ lineType: 'EXAMINATION', status: 'ACTIVE' }] }, paymentStatus: 'PAID' }] },
  prescriptions: [], blockers: [],
})

test('AT18: no prescription with saved FINAL and paid examination passes known checks', () => {
  const rows = completionChecks(eligible())
  assert.deepEqual(rows.slice(0, 4).map((row) => row.status), ['pass', 'pass', 'pass', 'pass'])
  assert.equal(rows[4].status, 'warning') // No independent order list in the current API.
})
test('AT34: unpaid, partially allocated or draft examination invoices fail', () => {
  for (const paymentStatus of ['UNPAID', 'PARTIAL', 'NOT_DUE']) {
    const data = eligible()
    data.billing.invoices[0].paymentStatus = paymentStatus
    assert.equal(completionChecks(data)[1].status, 'fail')
  }
  const data = eligible()
  data.billing.invoices[0].invoice.status = 'DRAFT'
  assert.equal(completionChecks(data)[1].status, 'fail')
  data.billing.invoices = []
  assert.equal(completionChecks(data)[1].status, 'fail')
})
test('PRELIMINARY and cancelled examination lines cannot satisfy completion', () => {
  const data = eligible()
  data.clinical.diagnoses[0].diagnosisType = 'PRELIMINARY'
  data.billing.invoices[0].invoice.lines[0].status = 'CANCELLED'
  assert.equal(completionChecks(data)[0].status, 'fail')
  assert.equal(completionChecks(data)[1].status, 'fail')
})
test('draft prescriptions and unsaved input fail with specific reasons', () => {
  const data = eligible()
  data.prescriptions = [{ status: 'DRAFT' }]
  data.blockers = ['Chẩn đoán đang soạn.']
  const rows = completionChecks(data)
  assert.equal(rows[2].status, 'fail')
  assert.equal(rows[3].detail, 'Chẩn đoán đang soạn.')
  data.prescriptions[0].status = 'ISSUED'
  assert.equal(completionChecks(data)[2].status, 'pass')
})
test('CLS line is rejected in P23 even if cancelled; never infer order absence', () => {
  const data = eligible()
  data.billing.invoices[0].invoice.lines.push({ lineType: 'CLS', status: 'CANCELLED' })
  assert.equal(completionChecks(data)[4].status, 'fail')
  assert.ok(completionChecks(eligible())[4].detail.includes('hệ thống sẽ kiểm tra lại'))
})
test('missing API data remains warning rather than fabricated success', () => {
  const rows = completionChecks({})
  assert.deepEqual(rows.map((row) => row.status), ['warning', 'warning', 'warning', 'pass', 'warning'])
})
