import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type { Payment, RoleCode } from '../api.ts'
import { Badge, Button, Card, CardTitle, Empty, Notice, ProvenanceBadge, StatusBadge, won } from '../ui.tsx'

/** Payment instructions and the simulated bank that answers them. */
export function Payments({ role }: { role: RoleCode }) {
  const queryClient = useQueryClient()
  const { data: payments = [] } = useQuery({ queryKey: ['payments'], queryFn: () => get<Payment[]>('/payments') })
  // Bank replies are applied in the background, so a row stays SENT for a moment after it was answered.
  // Rows already answered stay locked until their status changes.
  const [answered, setAnswered] = useState<ReadonlySet<number>>(new Set())
  const markAnswered = (ids: number[]) => setAnswered((previous) => new Set([...previous, ...ids]))
  const refresh = () => queryClient.invalidateQueries()
  const replyAll = useMutation({
    mutationFn: (ids: number[]) => post('/payments/bank-replies', role, {}).then(() => ids),
    onSuccess: (ids) => {
      markAnswered(ids)
      return refresh()
    },
  })
  const reply = useMutation({
    mutationFn: ({ id, succeeded }: { id: number; succeeded: boolean }) =>
      post(`/payments/${id}/bank-reply`, role, { succeeded }).then(() => id),
    onSuccess: (id) => {
      markAnswered([id])
      return refresh()
    },
  })
  const waiting = payments.filter((payment) => payment.status === 'SENT' && !answered.has(payment.id))
  const applying = payments.filter((payment) => payment.status === 'SENT' && answered.has(payment.id)).length
  const held = payments.filter((payment) => payment.status === 'HELD')
  const offsetReview = payments.filter((payment) => payment.status === 'OFFSET_REVIEW' || payment.status === 'OFFSET_DISPUTED')
  const error = replyAll.error ?? reply.error

  return (
    <div className="flex h-full flex-col gap-4 overflow-y-auto pb-6">
      <Card>
        <div className="flex flex-wrap items-center gap-3">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <h3 className="text-[15px] font-bold">모의 은행</h3>
              <ProvenanceBadge code="MOCK_REPLY" />
            </div>
            <p className="mt-1 text-[13px] leading-relaxed text-ink-soft">
              실제 이체는 일어나지 않습니다. 은행 회신을 여기서 대신 입력하면 시스템이 회신을 받아 지급 상태를 직접
              바꿉니다.
            </p>
          </div>
          <Button
            className="shrink-0 whitespace-nowrap"
            disabled={waiting.length === 0 || replyAll.isPending || reply.isPending}
            onClick={() => replyAll.mutate(waiting.map((payment) => payment.id))}
          >
            {replyAll.isPending ? '회신 보내는 중…' : `대기 ${waiting.length}건 이체 완료 회신`}
          </Button>
        </div>
        {applying > 0 && <p className="mt-2 text-xs font-semibold text-brand-strong">은행 회신 {applying}건 반영 중…</p>}
        {error && <div className="mt-3"><Notice tone="error">{error.message}</Notice></div>}
        {offsetReview.length > 0 && (
          <div className="mt-3">
            <Notice tone="info">
              상계 검토 {offsetReview.length}건: 90일 넘게 연체된 대리점이라 회계·자금팀이 상계 여부를 정할 때까지 은행으로 보내지
              않습니다(교육용 가정). 회계·자금팀 역할의 처리할 일에서 결정합니다.
            </Notice>
          </div>
        )}
        {held.length > 0 && (
          <div className="mt-3">
            <Notice tone="warn">
              지급 보류 {held.length}건: 실제 입금 계좌가 등록 정산계좌와 달라 대리점에 지급 계좌를 확인하고 있습니다(판단 규칙은
              교육용 가정). 변경·예외 화면에서 대리점 회신을 입력하면 보류가 풀리고 은행으로 전송됩니다.
            </Notice>
          </div>
        )}
      </Card>

      <Card>
        <CardTitle aside={<span className="text-xs text-ink-mute">{payments.length}건</span>}>지급 지시</CardTitle>
        {payments.length === 0 ? (
          <Empty>승인된 장려금이 없어 지급 지시가 없습니다.</Empty>
        ) : (
          <div className="@container overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-line text-left text-xs text-ink-mute">
                  <th className="py-2 pr-3 font-medium">대리점 · 장려금</th>
                  <th className="py-2 pr-3 text-right font-medium">장려금</th>
                  <th className="py-2 pr-3 text-right font-medium">상계</th>
                  <th className="py-2 pr-3 text-right font-medium">현금 지급</th>
                  <th className="py-2 pr-3 font-medium">상태</th>
                  <th className="hidden py-2 pr-3 font-medium @4xl:table-cell">지급 계좌</th>
                  <th className="hidden py-2 pr-3 font-medium @5xl:table-cell">은행 처리</th>
                  <th className="py-2 text-right font-medium">모의 회신</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((payment) => (
                  <tr key={payment.id} className="border-b border-line align-middle last:border-0">
                    <td className="py-2.5 pr-3">
                      <div className="flex items-baseline gap-1.5 whitespace-nowrap">
                        <span className="num text-xs text-ink-mute">#{payment.id}</span>
                        <span className="font-semibold">{payment.agencyId}</span>
                        <span className="truncate text-ink-soft">{payment.agencyName}</span>
                      </div>
                      <div className="mt-0.5 flex items-center gap-1.5 whitespace-nowrap text-xs text-ink-mute">
                        {payment.referenceId} <Badge tone="gray">{payment.period}</Badge>
                      </div>
                    </td>
                    <td className="num whitespace-nowrap py-2.5 pr-3 text-right text-ink-soft">{won(payment.amountKrw)}</td>
                    <td className="num whitespace-nowrap py-2.5 pr-3 text-right text-brand-strong" title={payment.offsetBasis ?? undefined}>
                      {payment.offsetKrw > 0 ? `−${won(payment.offsetKrw)}` : <span className="text-ink-mute">—</span>}
                      {payment.status === 'OFFSET_REVIEW' && payment.offsetKrw > 0 && <div className="text-[11px] text-ink-mute">제안</div>}
                    </td>
                    <td className="num whitespace-nowrap py-2.5 pr-3 text-right font-semibold">{won(payment.netKrw)}</td>
                    <td className="py-2.5 pr-3" title={payment.holdReason ?? undefined}>
                      <StatusBadge status={payment.status} />
                      {payment.holdReason && (
                        <div className="mt-1 max-w-56 text-[11px] leading-snug text-ink-mute">
                          {payment.status === 'HELD' ? '보류: ' : '보류 해제됨: '}
                          {payment.holdReason.replace(/ — 지급 계좌 미확인.*$/, '')}
                        </div>
                      )}
                    </td>
                    <td className="hidden whitespace-nowrap py-2.5 pr-3 text-xs text-ink-soft @4xl:table-cell">
                      {payment.payeeAccount ?? '—'}
                    </td>
                    <td className="hidden whitespace-nowrap py-2.5 pr-3 font-mono text-[11px] leading-relaxed text-ink-soft @5xl:table-cell">
                      <div title="전송 접수번호">{payment.gatewayReference ?? '—'}</div>
                      {payment.bankReference && <div title="은행 거래번호">{payment.bankReference}</div>}
                    </td>
                    <td className="whitespace-nowrap py-2.5 text-right">
                      {payment.status === 'SENT' && answered.has(payment.id) && (
                        <span className="text-xs text-ink-mute">반영 중…</span>
                      )}
                      {payment.status === 'SENT' && !answered.has(payment.id) && (
                        <span className="inline-flex gap-1.5">
                          <Button size="sm" kind="secondary" className="whitespace-nowrap" disabled={reply.isPending || replyAll.isPending} onClick={() => reply.mutate({ id: payment.id, succeeded: true })}>
                            완료
                          </Button>
                          <Button size="sm" kind="danger" className="whitespace-nowrap" disabled={reply.isPending || replyAll.isPending} onClick={() => reply.mutate({ id: payment.id, succeeded: false })}>
                            실패
                          </Button>
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
