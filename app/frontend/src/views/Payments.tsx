import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { get, post } from '../api.ts'
import type { Payment, RoleCode } from '../api.ts'
import { Badge, Button, Card, CardTitle, Empty, Notice, ProvenanceBadge, StatusBadge, won } from '../ui.tsx'

/** Payment instructions and the simulated bank that answers them. */
export function Payments({ role }: { role: RoleCode }) {
  const queryClient = useQueryClient()
  const { data: payments = [] } = useQuery({ queryKey: ['payments'], queryFn: () => get<Payment[]>('/payments') })
  const refresh = () => void queryClient.invalidateQueries()
  const replyAll = useMutation({ mutationFn: () => post('/payments/bank-replies', role, {}), onSuccess: refresh })
  const reply = useMutation({
    mutationFn: ({ id, succeeded }: { id: number; succeeded: boolean }) =>
      post(`/payments/${id}/bank-reply`, role, { succeeded }),
    onSuccess: refresh,
  })
  const waiting = payments.filter((payment) => payment.status === 'SENT')
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
          <Button disabled={waiting.length === 0 || replyAll.isPending} onClick={() => replyAll.mutate()}>
            대기 {waiting.length}건 이체 완료 회신
          </Button>
        </div>
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
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-line text-left text-xs text-ink-mute">
                <th className="py-2 font-medium">지시</th>
                <th className="py-2 font-medium">대리점</th>
                <th className="py-2 font-medium">장려금</th>
                <th className="py-2 text-right font-medium">장려금</th>
                <th className="py-2 text-right font-medium">상계</th>
                <th className="py-2 text-right font-medium">현금 지급</th>
                <th className="py-2 pl-4 font-medium">상태</th>
                <th className="py-2 font-medium">지급 계좌</th>
                <th className="py-2 font-medium">전송 접수번호</th>
                <th className="py-2 font-medium">은행 거래번호</th>
                <th className="py-2 text-right font-medium">모의 회신</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((payment) => (
                <tr key={payment.id} className="border-b border-line last:border-0">
                  <td className="num py-2.5 font-semibold">#{payment.id}</td>
                  <td className="py-2.5">
                    <span className="font-semibold">{payment.agencyId}</span>{' '}
                    <span className="text-ink-soft">{payment.agencyName}</span>
                  </td>
                  <td className="py-2.5 text-ink-soft">
                    {payment.referenceId} <Badge tone="gray">{payment.period}</Badge>
                  </td>
                  <td className="num py-2.5 text-right text-ink-soft">{won(payment.amountKrw)}</td>
                  <td className="num py-2.5 text-right text-brand-strong" title={payment.offsetBasis ?? undefined}>
                    {payment.offsetKrw > 0 ? `−${won(payment.offsetKrw)}` : '—'}
                    {payment.status === 'OFFSET_REVIEW' && payment.offsetKrw > 0 && <div className="text-[11px] text-ink-mute">제안</div>}
                  </td>
                  <td className="num py-2.5 text-right font-semibold">{won(payment.netKrw)}</td>
                  <td className="py-2.5 pl-4" title={payment.holdReason ?? undefined}>
                    <StatusBadge status={payment.status} />
                    {payment.holdReason && (
                      <div className="mt-1 max-w-64 text-[11px] leading-snug text-ink-mute">
                        {payment.status === 'HELD' ? '보류: ' : '보류 해제됨: '}
                        {payment.holdReason.replace(/ — 지급 계좌 미확인.*$/, '')}
                      </div>
                    )}
                  </td>
                  <td className="py-2.5 text-xs text-ink-soft">{payment.payeeAccount ?? '—'}</td>
                  <td className="py-2.5 font-mono text-xs text-ink-soft">{payment.gatewayReference ?? '—'}</td>
                  <td className="py-2.5 font-mono text-xs text-ink-soft">{payment.bankReference ?? '—'}</td>
                  <td className="py-2.5 text-right">
                    {payment.status === 'SENT' && (
                      <span className="inline-flex gap-1.5">
                        <Button size="sm" kind="secondary" disabled={reply.isPending} onClick={() => reply.mutate({ id: payment.id, succeeded: true })}>
                          완료
                        </Button>
                        <Button size="sm" kind="danger" disabled={reply.isPending} onClick={() => reply.mutate({ id: payment.id, succeeded: false })}>
                          실패
                        </Button>
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
