import { test } from 'node:test'
import assert from 'node:assert/strict'
import { suggestQuantity, duplicateWarnings, validatePrescriptionItem, formatDose } from './prescriptionUtils.js'

test('quantity uses exact entered dose/unit and frequency without prescribing a dose', () => {
  const item = { dose: '1', frequency: '2 lần/ngày', durationDays: '3', doseUnit: 'viên', dispensingUnit: 'viên' }
  assert.equal(suggestQuantity(item), '6')
  assert.equal(suggestQuantity({ ...item, dose: '0.5' }), '3')
  assert.equal(suggestQuantity({ ...item, dose: '0,125' }), '0.75')
  for (const change of [
    { dose: '' }, { dose: '500 mg' }, { frequency: 'Khi đau, tối đa 2 lần/ngày' },
    { frequency: 'Sáng và tối' }, { doseUnit: 'mL', dispensingUnit: 'chai', dose: '5' },
    { doseUnit: 'g', dispensingUnit: 'tuýp', dose: '1' }, { dispensingUnit: null },
    { dose: '1/7', frequency: '1 lần/ngày', durationDays: '1' },
    { dose: '999999999' }, { durationDays: '0' },
  ]) assert.equal(suggestQuantity({ ...item, ...change }), null)
})

test('duplicates use Drug identity or exact normalized ingredient components', () => {
  const items = [{ drugId: 1, ingredient: 'Amoxicillin + Clavulanic acid' }]
  assert.match(duplicateWarnings(items, { drugId: 1 })[0], /Trùng thuốc/)
  assert.match(duplicateWarnings(items, { drugId: 2, ingredient: ' AMOXICILLIN ' })[0], /Trùng hoạt chất/)
  assert.deepEqual(duplicateWarnings(items, { drugId: 2, ingredient: 'Amoxicillin sodium' }), [])
  assert.deepEqual(duplicateWarnings(items, items[0], 0), [])
})

test('required fields and decimal constraints reject unsafe payloads', () => {
  const item = { drugId: 1, dose: '1', route: 'Uống', frequency: '2 lần/ngày', durationDays: '3', quantity: '6', doseUnit: 'viên', dispensingUnit: 'viên' }
  assert.deepEqual(validatePrescriptionItem(item), {})
  assert.deepEqual(validatePrescriptionItem({ ...item, quantity: '10,125' }), {})
  for (const quantity of ['0', '-1', '1.0001', '1000000000', 'NaN']) {
    assert.ok(validatePrescriptionItem({ ...item, quantity }).quantity)
  }
  for (const dose of ['0', '-1', '1/2', '1 viên', 'abc', '0,00', '1e3']) assert.ok(validatePrescriptionItem({ ...item, dose }).dose)
  for (const dose of ['1', '0,5', '0.5', '500']) assert.equal(validatePrescriptionItem({ ...item, dose }).dose, undefined)
  assert.equal(Object.keys(validatePrescriptionItem({})).length, 6)
})

test('dose display uses catalogue unit and preserves legacy snapshots', () => {
  assert.equal(formatDose('0.5', 'viên'), '0,5 viên')
  assert.equal(formatDose('1 viên', 'viên'), '1 viên')
})
