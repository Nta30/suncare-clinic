export function completionChecks({ clinical, billing, prescriptions, blockers = [] }) {
  const row = (name, status, detail) => ({ name, status, detail })
  const diagnosis = clinical ? row('Chẩn đoán xác định', clinical.diagnoses.some((item) => item.diagnosisType === 'FINAL') ? 'pass' : 'fail',
    clinical.diagnoses.some((item) => item.diagnosisType === 'FINAL') ? 'Đã lưu chẩn đoán FINAL.' : 'Chưa có chẩn đoán FINAL được lưu.') : row('Chẩn đoán xác định', 'warning', 'Chưa đọc được dữ liệu chẩn đoán.')
  let payment = row('Phí khám', 'warning', 'Chưa đọc được dữ liệu thanh toán.')
  let cls = row('Chỉ định cận lâm sàng', 'warning', 'Chưa đọc được dữ liệu chỉ định liên quan hóa đơn.')
  if (billing) {
    const documents = billing.invoices.filter(({ invoice }) => invoice.status !== 'VOID')
    const fee = documents.some(({ invoice }) => invoice.status === 'ISSUED' && invoice.lines.some((line) => line.lineType === 'EXAMINATION' && line.status === 'ACTIVE'))
    const settled = fee && documents.every(({ invoice, paymentStatus }) => invoice.status === 'ISSUED' && ['PAID', 'NOT_APPLICABLE'].includes(paymentStatus))
    payment = row('Phí khám', settled ? 'pass' : 'fail', settled ? 'Phí khám đã thanh toán đầy đủ.' : 'Chưa phát hành phí khám, còn hóa đơn nháp hoặc chưa thanh toán đủ.')
    const hasCls = billing.invoices.some(({ invoice }) => invoice.lines.some((line) => line.lineType === 'CLS'))
    cls = hasCls ? row('Chỉ định cận lâm sàng', 'fail', 'Có dòng CLS. Lượt khám có chỉ định CLS chưa hỗ trợ hoàn tất tại đây.')
      : row('Chỉ định cận lâm sàng', 'warning', 'Chưa thấy dòng CLS trong hóa đơn. Chưa xác nhận được toàn bộ chỉ định; hệ thống sẽ kiểm tra lại khi hoàn tất.')
  }
  let prescription = row('Đơn thuốc', 'warning', 'Chưa đọc được dữ liệu đơn thuốc.')
  if (prescriptions) {
    const ready = prescriptions.every((item) => item.status === 'ISSUED')
    prescription = row('Đơn thuốc', ready ? 'pass' : 'fail', ready ? prescriptions.length ? 'Đơn thuốc đã phát hành, không còn DRAFT.' : 'Không kê đơn thuốc.' : 'Còn đơn thuốc DRAFT; cần phát hành hoặc bỏ đơn nháp.')
  }
  return [diagnosis, payment, prescription, row('Dữ liệu khám', blockers.length ? 'fail' : 'pass', blockers.length ? blockers.join(' ') : 'Không còn thay đổi chưa lưu hoặc thao tác đang xử lý.'), cls]
}
