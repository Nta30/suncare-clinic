
export async function getHealth(signal) {
  const response = await fetch('/api/health', {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
    cache: 'no-store',
    signal,
  })

  if (response.status !== 200) {
    throw new Error(
      `Health API trả về HTTP ${response.status}.`
    )
  }

  let data

  try {
    data = await response.json()
  } catch {
    throw new Error(
      'Backend không trả về JSON hợp lệ.'
    )
  }

  if (
    data?.status !== 'UP' ||
    data?.service !== 'suncare-clinic'
  ) {
    throw new Error(
      'Dữ liệu Health API không đúng định dạng mong đợi.'
    )
  }

  return data
}
