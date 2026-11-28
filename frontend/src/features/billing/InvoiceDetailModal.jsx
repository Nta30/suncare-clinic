import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faPrint, faXmark } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import { getInvoicePrintDetail } from '../../services/encounterApi'
import InvoiceContent from './InvoiceContent'
import styles from './InvoiceDetail.module.css'

export default function InvoiceDetailModal({
  encounterId,
  invoiceId,
  onClose,
  onSessionExpired
}) {
  const id = useId()
  const dialog = useRef(null)
  const opener = useRef(null)
  const closing = useRef(false)
  const timer = useRef(null)
  const printLock = useRef(false)
  const [state, setState] = useState({ loading: true })
  const [revision, setRevision] = useState(0)
  const [printing, setPrinting] = useState(false)
  useEffect(() => {
    opener.current = document.activeElement
    dialog.current?.showModal()
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.clearTimeout(timer.current)
      document.body.style.overflow = overflow
      if (opener.current?.isConnected) opener.current.focus?.()
      else document.getElementById(`invoice-view-${invoiceId}`)?.focus()
    }
  }, [invoiceId])
  useEffect(() => {
    const controller = new AbortController()
    getInvoicePrintDetail(encounterId, invoiceId, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return
        if (
          data.invoice?.id !== invoiceId ||
          !Array.isArray(data.invoice.lines) ||
          !Array.isArray(data.rooms)
        )
          throw new Error('Dữ liệu chi tiết hóa đơn không hợp lệ.')
        setState({ data })
      })
      .catch((failure) => {
        if (controller.signal.aborted) return
        if (failure.status === 401) onSessionExpired(failure.message)
        else setState({ error: failure.message })
      })
    return () => controller.abort()
  }, [encounterId, invoiceId, revision, onSessionExpired])
  const printable = state.data?.invoice.status === 'ISSUED'
  useEffect(() => {
    if (!printable) return undefined
    document.body.dataset.suncareInvoicePrint = 'true'
    return () => {
      delete document.body.dataset.suncareInvoicePrint
    }
  }, [printable])
  function close() {
    if (closing.current || printLock.current) return
    closing.current = true
    dialog.current?.classList.add('logout-dialog--closing')
    timer.current = window.setTimeout(
      () => {
        dialog.current?.close()
        onClose()
      },
      window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180
    )
  }
  async function print() {
    if (!printable || printLock.current) return
    printLock.current = true
    setPrinting(true)
    try {
      await document.fonts.ready
      if (dialog.current?.open) window.print()
    } finally {
      printLock.current = false
      setPrinting(false)
    }
  }
  return createPortal(
    <>
      <dialog
        ref={dialog}
        aria-labelledby={`${id}-title`}
        onCancel={(event) => {
          event.preventDefault()
          close()
        }}
        onClick={(event) => {
          if (event.target === event.currentTarget) {
            const r = event.currentTarget.getBoundingClientRect()
            if (
              event.clientX < r.left ||
              event.clientX > r.right ||
              event.clientY < r.top ||
              event.clientY > r.bottom
            )
              close()
          }
        }}
        className={`logout-dialog ${styles.dialog}`}
      >
        <header className={styles.modalHeader}>
          <h2 id={`${id}-title`} className='text-xl font-bold'>
            Chi tiết hóa đơn
          </h2>
          <Button
            autoFocus
            onClick={close}
            disabled={printing}
            aria-label='Đóng chi tiết hóa đơn'
            className='shrink-0 px-3'
          >
            <FontAwesomeIcon icon={faXmark} />
          </Button>
        </header>
        <div className={styles.scroll} aria-busy={state.loading}>
          {state.loading && (
            <p role='status' className='p-5 text-sm'>
              Đang tải chi tiết hóa đơn...
            </p>
          )}
          {state.error && (
            <div
              role='alert'
              className='space-y-3 p-5 text-sm text-clinical-danger'
            >
              <p>{state.error}</p>
              <Button
                onClick={() => {
                  setState({ loading: true })
                  setRevision((value) => value + 1)
                }}
              >
                Thử lại chi tiết hóa đơn
              </Button>
            </div>
          )}
          {state.data && (
            <>
              {!printable && (
                <p className='mb-3 text-sm text-clinical-muted'>
                  Chỉ in hóa đơn đã phát hành. Nội dung dưới đây để đối chiếu.
                </p>
              )}
              <InvoiceContent detail={state.data} />
            </>
          )}
        </div>
        <footer className={styles.modalFooter}>
          <Button onClick={close} disabled={printing}>
            Đóng
          </Button>
          <Button
            variant='primary'
            onClick={print}
            disabled={!printable || printing}
            title={
              !printable ? 'Chỉ in hóa đơn đã phát hành' : 'In hóa đơn khổ A4'
            }
          >
            <FontAwesomeIcon icon={faPrint} />
            {printing ? 'Đang mở hộp thoại in...' : 'In hóa đơn'}
          </Button>
        </footer>
      </dialog>
      {printable && (
        <div data-suncare-invoice-paper className={styles.printRoot}>
          <InvoiceContent detail={state.data} />
        </div>
      )}
    </>,
    document.body
  )
}
