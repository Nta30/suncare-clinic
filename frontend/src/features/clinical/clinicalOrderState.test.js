import test from 'node:test'
import assert from 'node:assert/strict'
import {
  clinicalTotals,
  encounterSteps,
  performingDepartment
} from './clinicalOrderState.js'

test('department is derived only from a valid room or a single supported department', () => {
  const service = {
    rooms: [
      { roomId: 1, departmentId: 4 },
      { roomId: 2, departmentId: 4 }
    ]
  }
  assert.equal(performingDepartment(service, ''), 4)
  assert.equal(performingDepartment(service, '2'), 4)
  assert.equal(performingDepartment(service, '999'), null)
  assert.equal(performingDepartment(null, ''), null)
  service.rooms.push({ roomId: 3, departmentId: 5 })
  assert.equal(performingDepartment(service, ''), null)
  assert.equal(performingDepartment(service, '3'), 5)
})
test('three/four steps preserve stable identities and billing is always last', () => {
  assert.deepEqual(
    encounterSteps(false).map(([key]) => key),
    ['clinical', 'treatment', 'invoices']
  )
  assert.deepEqual(
    encounterSteps(true).map(([key]) => key),
    ['clinical', 'orders', 'treatment', 'invoices']
  )
})
test('CLS totals preserve integer precision and use actual line allocation', () => {
  const rows = [
    {
      amount: '9007199254740993',
      paidAmount: '50000',
      outstandingAmount: '9007199254690993'
    }
  ]
  assert.deepEqual(clinicalTotals(rows), {
    total: 9007199254740993n,
    paid: 50000n,
    outstanding: 9007199254690993n
  })
})
test('cancelled orders/lines are excluded; missing accounting data remains unknown', () => {
  assert.deepEqual(
    clinicalTotals([{ status: 'CANCELLED' }, { lineStatus: 'CANCELLED' }]),
    { total: 0n, paid: 0n, outstanding: 0n }
  )
  assert.equal(clinicalTotals([{ amount: '150000', paidAmount: null }]), null)
})
