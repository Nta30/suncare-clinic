import test from 'node:test'
import assert from 'node:assert/strict'
import { invoiceMoney, roomDescription } from './invoicePresentation.js'

test('invoice presentation preserves integer precision', () => {
  assert.equal(invoiceMoney('9007199254740993'), '9.007.199.254.740.993 đ')
  assert.equal(invoiceMoney(null), '—')
})
test('each CLS line gets its own room, including duplicate services', () => {
  const rooms = [{ lineId: 2, roomName: 'Phòng A', roomCode: 'A', source: 'PREFERRED' }, { lineId: 3, roomName: 'Phòng B', roomCode: 'B', source: 'ASSIGNED' }]
  assert.equal(roomDescription({ id: 1, lineType: 'EXAMINATION' }, rooms), '—')
  assert.equal(roomDescription({ id: 2, lineType: 'CLS' }, rooms), 'Phòng A · A (Ưu tiên)')
  assert.equal(roomDescription({ id: 3, lineType: 'CLS' }, rooms), 'Phòng B · B')
  assert.equal(roomDescription({ id: 4, lineType: 'CLS' }, rooms), 'Chờ phân phòng')
})
