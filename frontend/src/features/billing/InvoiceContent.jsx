import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faHeartPulse } from '@fortawesome/free-solid-svg-icons'
import styles from './InvoiceDetail.module.css'

import {
  invoiceMoney,
  invoiceTime,
  roomDescription
} from './invoicePresentation'

function InvoiceServiceRow({ line, index, hasClinical, rooms }) {
  return (
    <tr data-invoice-service-line>
      <td>{index + 1}</td>
      <td>{line.serviceName}</td>
      {hasClinical && <td>{roomDescription(line, rooms)}</td>}
      <td>
        {line.quantity} {line.unitName}
      </td>
      <td className={styles.amount}>{invoiceMoney(line.unitPrice)}</td>
      <td className={styles.amount}>{invoiceMoney(line.amount)}</td>
    </tr>
  )
}

export default function InvoiceContent({ detail }) {
  const { invoice, patientCode, encounterCode, rooms } = detail
  const hasClinical = invoice.lines.some((line) => line.lineType === 'CLS')
  return (
    <article
      className={styles.paper}
      aria-label={`Nội dung hóa đơn ${invoice.code}`}
    >
      <header className={styles.brandHeader}>
        <div className={styles.logo}>
          <span>
            <FontAwesomeIcon icon={faHeartPulse} aria-hidden='true' />
          </span>
          <div>
            <strong>SUNCARE CLINIC</strong>
            <small>Phòng khám đa khoa</small>
          </div>
        </div>
        <div className={styles.facility}>
          <h3>PHÒNG KHÁM ĐA KHOA SUNCARE</h3>
          {invoice.facilityAddress && <p>{invoice.facilityAddress}</p>}
        </div>
      </header>
      <h2 className={styles.title}>HÓA ĐƠN VIỆN PHÍ</h2>
      <dl className={styles.identifiers}>
        <div>
          <dt>Mã hóa đơn:</dt>
          <dd className={styles.code}>{invoice.code}</dd>
        </div>
        <div>
          <dt>Ngày giờ phát hành:</dt>
          <dd>{invoiceTime(invoice.issuedAt)}</dd>
        </div>
      </dl>
      <dl className={styles.patient}>
        <div>
          <dt>Họ và tên:</dt>
          <dd>
            <strong>{invoice.patientName}</strong>
          </dd>
        </div>
        {patientCode && (
          <div>
            <dt>Mã bệnh nhân:</dt>
            <dd className={styles.code}>{patientCode}</dd>
          </div>
        )}
        <div>
          <dt>Địa chỉ:</dt>
          <dd>{invoice.patientAddress || 'Chưa ghi nhận'}</dd>
        </div>
        {encounterCode && (
          <div>
            <dt>Mã lượt khám:</dt>
            <dd className={styles.code}>{encounterCode}</dd>
          </div>
        )}
      </dl>
      <div
        className={styles.tableViewport}
        role='region'
        aria-label='Chi tiết dịch vụ hóa đơn'
        tabIndex={0}
      >
        <table className={styles.services}>
          <thead>
            <tr>
              <th className={styles.sequence}>STT</th>
              <th>Tên dịch vụ</th>
              {hasClinical && <th>Phòng thực hiện</th>}
              <th className={styles.quantity}>SL</th>
              <th className={styles.amount}>Đơn giá</th>
              <th className={styles.amount}>Thành tiền</th>
            </tr>
          </thead>
          <tbody>
            {invoice.lines.slice(0, -1).map((line, index) => (
              <InvoiceServiceRow
                key={line.id}
                {...{ line, index, hasClinical, rooms }}
              />
            ))}
          </tbody>
          <tbody className={styles.invoiceTail}>
            {invoice.lines.length > 0 && (
              <InvoiceServiceRow
                line={invoice.lines.at(-1)}
                index={invoice.lines.length - 1}
                {...{ hasClinical, rooms }}
              />
            )}
            <tr className={styles.totalRow}>
              <th scope='row' colSpan={hasClinical ? 5 : 4}>
                Tổng giá trị hóa đơn:
              </th>
              <td className={styles.amount}>
                {invoiceMoney(invoice.totalAmount)}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  )
}
