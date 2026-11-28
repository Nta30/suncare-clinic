export const money = (value) =>
  value == null
    ? '—'
    : `${new Intl.NumberFormat('vi-VN').format(BigInt(value))} ₫`

export function performingDepartment(service, roomId) {
  const rooms = service?.rooms || []
  if (roomId)
    return (
      rooms.find((room) => String(room.roomId) === String(roomId))
        ?.departmentId ?? null
    )
  const departments = [...new Set(rooms.map((room) => room.departmentId))]
  return departments.length === 1 ? departments[0] : null
}

export function clinicalTotals(orders) {
  const active = orders.filter(
    (order) => order.status !== 'CANCELLED' && order.lineStatus !== 'CANCELLED'
  )
  if (
    active.some((order) =>
      [order.amount, order.paidAmount, order.outstandingAmount].some(
        (value) => value == null
      )
    )
  )
    return null
  return active.reduce(
    (sum, order) => ({
      total: sum.total + BigInt(order.amount),
      paid: sum.paid + BigInt(order.paidAmount),
      outstanding: sum.outstanding + BigInt(order.outstandingAmount)
    }),
    { total: 0n, paid: 0n, outstanding: 0n }
  )
}

export function encounterSteps(withClinicalOrders) {
  return [
    ['clinical', 'Khám lâm sàng'],
    ...(withClinicalOrders ? [['orders', 'Cận lâm sàng']] : []),
    ['treatment', 'Chẩn đoán và điều trị'],
    ['invoices', 'Viện phí']
  ]
}
