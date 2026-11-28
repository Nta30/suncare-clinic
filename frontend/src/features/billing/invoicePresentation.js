export const invoiceMoney = (value) =>
  value == null
    ? '—'
    : `${new Intl.NumberFormat('vi-VN').format(BigInt(value))} đ`
export const invoiceTime = (value) =>
  value
    ? new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Saigon',
        dateStyle: 'short',
        timeStyle: 'short'
      }).format(new Date(value))
    : '—'

export function roomDescription(line, rooms) {
  if (line.lineType !== 'CLS') return '—'
  const room = rooms.find((item) => item.lineId === line.id)
  if (!room?.roomName || !['ASSIGNED', 'PREFERRED'].includes(room.source))
    return 'Chờ phân phòng'
  return `${room.roomName}${room.roomCode ? ` · ${room.roomCode}` : ''}${room.source === 'PREFERRED' ? ' (Ưu tiên)' : ''}`
}
