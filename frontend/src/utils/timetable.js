export const zone = 'Asia/Saigon'
export const time = (value) => new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', timeZone: zone }).format(new Date(value))
export const dateLabel = (value) => new Intl.DateTimeFormat('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit', timeZone: zone }).format(new Date(`${value}T12:00:00+07:00`))
export const dateKey = (date) => new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)
export function addDays(value, count) {
  const date = new Date(`${value}T12:00:00+07:00`)
  date.setUTCDate(date.getUTCDate() + count)
  return dateKey(date)
}
export function currentWeek() {
  const today = dateKey(new Date())
  const day = new Date(`${today}T12:00:00+07:00`).getUTCDay()
  return addDays(today, -((day + 6) % 7))
}

