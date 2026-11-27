const normalized = (value) => (value || '').normalize('NFC').trim().replace(/\s+/g, ' ').toLocaleLowerCase('vi')
const fold = (value) => normalized(value).normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd')

export function suggestQuantity({ dose, frequency, durationDays, doseUnit, dispensingUnit }) {
  if (!doseUnit || !dispensingUnit || normalized(doseUnit) !== normalized(dispensingUnit)) return null
  if (!['viên', 'ml', 'g', 'liều xịt', 'liều hít'].includes(normalized(doseUnit))) return null
  const doseMatch = normalized(dose).match(/^(\d+(?:[.,]\d{1,3})?)$/)
  const frequencyMatch = fold(frequency).match(/^([1-9]\d?)\s+lan\s*\/\s*ngay$/)
  if (!doseMatch || !frequencyMatch || !/^[1-9]\d{0,8}$/.test(String(durationDays))) return null
  const [whole, fraction = ''] = doseMatch[1].replace(',', '.').split('.')
  if (whole.length > 9) return null
  const numerator = BigInt(whole + fraction)
  const denominator = 10n ** BigInt(fraction.length)
  const scaled = numerator * BigInt(frequencyMatch[1]) * BigInt(durationDays) * 1000n
  if (scaled % denominator !== 0n) return null
  const total = scaled / denominator
  if (total <= 0n || total > 999999999999n) return null
  const decimal = String(total % 1000n).padStart(3, '0').replace(/0+$/, '')
  return String(total / 1000n) + (decimal ? '.' + decimal : '')
}

export function duplicateWarnings(items, candidate, excludeIndex = -1) {
  const parts = (value) => new Set((value || '').split('+').map(normalized).filter(Boolean))
  const ingredients = parts(candidate.ingredient)
  return items.flatMap((item, index) => {
    if (index === excludeIndex) return []
    if (item.drugId === candidate.drugId) return [`Trùng thuốc với dòng ${index + 1}.`]
    const shared = [...parts(item.ingredient)].filter((part) => ingredients.has(part))
    return shared.length ? [`Trùng hoạt chất với dòng ${index + 1}: ${shared.join(', ')}.`] : []
  })
}

export function validatePrescriptionItem(item) {
  const errors = {}
  if (!Number.isSafeInteger(item.drugId) || item.drugId <= 0) errors.drugId = 'Cần chọn thuốc đang sử dụng từ danh mục.'
  if (!item.doseUnit || !item.dispensingUnit) errors.drugId = 'Thuốc chưa có đủ đơn vị liều dùng/cấp phát. Cần rà soát danh mục.'
  for (const [key, label, limit] of [['dose', 'Liều mỗi lần', 200], ['route', 'Đường dùng', 100], ['frequency', 'Tần suất', 200]]) {
    if (!item[key]?.trim()) errors[key] = `Cần nhập ${label.toLocaleLowerCase('vi')}.`
    else if (item[key].length > limit) errors[key] = `${label} tối đa ${limit} ký tự.`
  }
  if (item.dose?.trim() && item.dose.length <= 200 && !validDose(item.dose)) errors.dose = 'Liều mỗi lần phải là số dương, ví dụ 1 hoặc 0,5.'
  if (!/^[1-9]\d*$/.test(String(item.durationDays)) || Number(item.durationDays) > 2147483647) errors.durationDays = 'Số ngày điều trị phải là số nguyên dương hợp lệ.'
  if (!/^\d{1,9}([.,]\d{1,3})?$/.test(String(item.quantity)) || !/[1-9]/.test(String(item.quantity))) errors.quantity = 'Số lượng phải dương, tối đa 9 chữ số nguyên và 3 chữ số thập phân.'
  if ((item.instruction || '').length > 1000) errors.instruction = 'Hướng dẫn sử dụng tối đa 1000 ký tự.'
  return errors
}

export function validDose(value) {
  const text = value.trim()
  return /^[0-9]+(?:[.,][0-9]+)?$/.test(text) && /[1-9]/.test(text)
}

export function formatDose(dose, unit) {
  return validDose(dose) ? dose.replace('.', ',') + ' ' + unit : dose
}
