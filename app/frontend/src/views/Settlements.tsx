import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { get } from '../api.ts'
import type { Period, ReviewSummary, RoleCode } from '../api.ts'
import { Badge, Card, CardTitle, Empty, ROLE_LABEL, StatusBadge, won } from '../ui.tsx'
import { ReviewDetail } from './ReviewDetail.tsx'

/** Month by month: what was due, how far settlement has got, and every review of the month. */
export function Settlements({ role }: { role: RoleCode }) {
  const { data: periods = [] } = useQuery({ queryKey: ['periods'], queryFn: () => get<Period[]>('/periods') })
  const [period, setPeriod] = useState<string | null>(null)
  const [reviewId, setReviewId] = useState<number | null>(null)
  const current = period ?? periods[0]?.period ?? null
  const { data: reviews = [] } = useQuery({
    queryKey: ['reviews', current],
    queryFn: () => get<ReviewSummary[]>(`/reviews?period=${current}`),
    enabled: current !== null,
  })

  return (
    <div className="flex h-full min-h-0 flex-col gap-4">
      <div className="grid grid-cols-3 gap-3 2xl:grid-cols-6">
        {periods.map((item) => (
          <button
            key={item.period}
            type="button"
            onClick={() => {
              setPeriod(item.period)
              setReviewId(null)
            }}
            className={`rounded-2xl bg-white p-4 text-left transition-shadow ${item.period === current ? 'ring-2 ring-brand' : 'hover:shadow-sm'}`}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-bold">{item.period}</span>
              {item.pendingCount > 0 && !item.batchOpened ? (
                <Badge tone="orange">접수 전</Badge>
              ) : item.batchOpened ? (
                <Badge tone="blue">정산 중</Badge>
              ) : (
                <Badge tone="gray">원본상 종결</Badge>
              )}
            </div>
            <div className="num mt-2 text-lg font-bold">{won(item.totalKrw)}</div>
            <div className="mt-1 text-xs text-ink-mute">
              {item.claimCount}건 · 지급 {item.paidCount + item.settledInSourceCount} · 반려 {item.rejectedCount}
            </div>
          </button>
        ))}
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,400px)_minmax(0,1fr)] gap-4">
        <Card className="min-h-0 overflow-y-auto">
          <CardTitle aside={<span className="text-xs text-ink-mute">{reviews.length}건</span>}>{current} 검토</CardTitle>
          {reviews.length === 0 ? (
            <Empty>
              이 달에는 이 시스템에서 연 검토가 없습니다. 원본 기록상 이미 처리된 달이거나 아직 정산을 접수하지
              않았습니다.
            </Empty>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-line text-left text-xs text-ink-mute">
                  <th className="py-2 font-medium">단위</th>
                  <th className="py-2 text-right font-medium">합산</th>
                  <th className="py-2 pl-3 font-medium">권한</th>
                  <th className="py-2 font-medium">상태</th>
                </tr>
              </thead>
              <tbody>
                {reviews.map((review) => (
                  <tr
                    key={review.id}
                    onClick={() => setReviewId(review.id)}
                    className={`cursor-pointer border-b border-line last:border-0 ${review.id === reviewId ? 'bg-brand-soft' : 'hover:bg-fill'}`}
                  >
                    <td className="py-2.5 pl-1">
                      <div className="flex items-center gap-1.5 font-semibold">
                        {review.unitKey}
                        {review.exceedsLimit && <Badge tone="red">초과</Badge>}
                      </div>
                      {review.relatedGroup && <div className="text-xs text-ink-mute">{review.memberAgencyIds.split(',').join(', ')}</div>}
                    </td>
                    <td className="num py-2.5 text-right font-semibold">{won(review.totalKrw)}</td>
                    <td className="py-2.5 pl-3 text-xs text-ink-soft">{ROLE_LABEL[review.requiredApprover]}</td>
                    <td className="py-2.5">
                      <StatusBadge status={review.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
        <div className="min-h-0 overflow-y-auto pb-6">
          {reviewId !== null ? (
            <ReviewDetail reviewId={reviewId} role={role} />
          ) : (
            <Card>
              <Empty>검토를 선택하면 근거와 처리 이력을 볼 수 있습니다.</Empty>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
