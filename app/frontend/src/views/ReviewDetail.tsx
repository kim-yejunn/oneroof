import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type { Group, HistoryMonth, ReviewDetail as Detail, RoleCode } from '../api.ts'
import {
  ACTION_LABEL,
  Badge,
  Button,
  Card,
  CardTitle,
  Empty,
  Notice,
  ProvenanceBadge,
  ROLE_LABEL,
  SourceRef,
  StatusBadge,
  won,
} from '../ui.tsx'

/** Everything needed to decide one review: the figures, the decision, the evidence and the trail. */
export function ReviewDetail({ reviewId, role }: { reviewId: number; role: RoleCode }) {
  const { data: review, error } = useQuery({
    queryKey: ['review', reviewId],
    queryFn: () => get<Detail>(`/reviews/${reviewId}`),
  })

  if (error) {
    return <Notice tone="error">{error.message}</Notice>
  }
  if (!review) {
    return <Empty>불러오는 중…</Empty>
  }

  return (
    <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
      <div className="flex min-w-0 flex-col gap-4">
        <DecisionCard review={review} role={role} />
        <ClaimsCard review={review} />
      </div>
      <div className="flex min-w-0 flex-col gap-4">
        {review.evidence && <EvidenceCard group={review.evidence} />}
        {review.history && <HistoryCard history={review.history} currentPeriod={review.period} />}
        <TimelineCard review={review} />
      </div>
    </div>
  )
}

function DecisionCard({ review, role }: { review: Detail; role: RoleCode }) {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const decide = useMutation({
    mutationFn: (approved: boolean) =>
      post(`/approvals/${review.approvalRequestId}/decision`, role, { approved, comment }),
    onSuccess: () => {
      setComment('')
      void queryClient.invalidateQueries()
    },
  })

  const over = review.totalKrw - review.limitKrw
  const ratio = Math.min(100, Math.round((review.limitKrw / Math.max(review.totalKrw, review.limitKrw)) * 100))
  const fill = Math.min(100, Math.round((review.totalKrw / Math.max(review.totalKrw, review.limitKrw)) * 100))
  const pending = review.approvalStatus === 'PENDING'
  const hasAuthority = role === review.requiredApprover

  return (
    <Card>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <StatusBadge status={review.status} />
        <Badge tone="gray">{review.period}</Badge>
        {review.relatedGroup ? <Badge tone="blue">관련자 그룹 합산</Badge> : <Badge tone="gray">단독</Badge>}
        <span className="text-xs text-ink-mute">검토 #{review.id}</span>
      </div>
      <h2 className="text-xl font-bold leading-snug">
        {review.relatedGroup ? `${review.unitKey} · ${review.memberAgencyIds.split(',').join(', ')}` : review.unitKey}
      </h2>

      <div className="mt-5 grid grid-cols-3 gap-3">
        <Figure label={review.relatedGroup ? '관련자 합산' : '월 지급액'} value={won(review.totalKrw)} tag={<ProvenanceBadge code="CALCULATED" />} />
        <Figure label="월 한도" value={won(review.limitKrw)} tag={<ProvenanceBadge code="ORIGINAL" />} />
        <Figure
          label={review.exceedsLimit ? '한도 초과' : '한도 여유'}
          value={won(Math.abs(over))}
          emphasis={review.exceedsLimit ? 'danger' : 'ok'}
          tag={<ProvenanceBadge code="CALCULATED" />}
        />
      </div>

      <div className="relative mt-4 h-2.5 rounded-full bg-fill">
        <div
          className={`h-full rounded-full ${review.exceedsLimit ? 'bg-danger' : 'bg-brand'}`}
          style={{ width: `${fill}%` }}
        />
        <div className="absolute -top-1 h-4.5 w-0.5 bg-ink" style={{ left: `${ratio}%` }} title="월 한도" />
      </div>
      <p className="mt-3 text-[13px] leading-relaxed text-ink-soft">
        승인 권한은 <b className="text-ink">{ROLE_LABEL[review.requiredApprover]}</b>입니다.{' '}
        {review.exceedsLimit
          ? '합산 금액이 월 한도를 넘어 영업지원팀 전결로 처리할 수 없습니다.'
          : '월 한도 이내라 영업지원팀 전결 사안입니다.'}
      </p>

      <div className="mt-5 border-t border-line pt-4">
        {pending ? (
          <div className="flex flex-col gap-3">
            {!hasAuthority && (
              <Notice tone="warn">
                현재 역할은 {ROLE_LABEL[role]}입니다. 이 건은 {ROLE_LABEL[review.requiredApprover]} 권한 사안이라 결정을
                보내면 서버가 거부합니다.
              </Notice>
            )}
            <input
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              placeholder="결정 사유 (선택)"
              className="h-10 rounded-xl border border-line px-3.5 text-sm outline-none focus:border-brand"
            />
            <div className="flex gap-2">
              <Button size="lg" className="flex-1" disabled={decide.isPending} onClick={() => decide.mutate(true)}>
                승인
              </Button>
              <Button kind="danger" size="lg" className="flex-1" disabled={decide.isPending} onClick={() => decide.mutate(false)}>
                반려
              </Button>
            </div>
            {decide.error && <Notice tone="error">{decide.error.message}</Notice>}
          </div>
        ) : review.approvalStatus ? (
          <div className="flex flex-wrap items-center gap-2 text-sm">
            {review.approvalStatus === 'APPROVED' ? <Badge tone="blue">승인</Badge> : <Badge tone="red">반려</Badge>}
            <span className="font-semibold">{review.decidedByName}</span>
            {review.decisionComment && <span className="text-ink-soft">“{review.decisionComment}”</span>}
            <ProvenanceBadge code="MOCK_REPLY" />
          </div>
        ) : (
          <p className="text-sm text-ink-mute">승인 요청을 만드는 중…</p>
        )}
      </div>
    </Card>
  )
}

function Figure({
  label,
  value,
  tag,
  emphasis,
}: {
  label: string
  value: string
  tag: React.ReactNode
  emphasis?: 'danger' | 'ok'
}) {
  const color = emphasis === 'danger' ? 'text-danger' : emphasis === 'ok' ? 'text-success' : 'text-ink'
  return (
    <div className="rounded-xl bg-fill px-4 py-3.5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium text-ink-soft">{label}</span>
        {tag}
      </div>
      <div className={`num mt-1.5 text-[22px] font-bold ${color}`}>{value}</div>
    </div>
  )
}

function ClaimsCard({ review }: { review: Detail }) {
  return (
    <Card>
      <CardTitle aside={<span className="text-xs text-ink-mute">{review.claims.length}건</span>}>장려금 내역</CardTitle>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-line text-left text-xs text-ink-mute">
            <th className="py-2 font-medium">대리점</th>
            <th className="py-2 text-right font-medium">금액</th>
            <th className="py-2 pl-4 font-medium">상태</th>
            <th className="py-2 font-medium">지급</th>
            <th className="py-2 font-medium">원본 위치</th>
          </tr>
        </thead>
        <tbody>
          {review.claims.map((claim) => (
            <tr key={claim.rebateId} className="border-b border-line last:border-0">
              <td className="py-2.5">
                <div className="font-semibold">{claim.agencyId}</div>
                <div className="text-xs text-ink-mute">{claim.agencyName}</div>
              </td>
              <td className="num py-2.5 text-right font-semibold">{won(claim.amountKrw)}</td>
              <td className="py-2.5 pl-4">
                <StatusBadge status={claim.settlementStatus} />
              </td>
              <td className="py-2.5">
                <StatusBadge status={claim.paymentStatus} />
              </td>
              <td className="py-2.5">
                <SourceRef value={claim.sourceRef} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  )
}

export function EvidenceCard({ group }: { group: Group }) {
  return (
    <Card>
      <CardTitle aside={<Badge tone="blue">{group.groupKey}</Badge>}>관련자로 본 근거</CardTitle>
      <p className="mb-3 text-[13px] leading-relaxed text-ink-soft">
        대표자·주소·정산계좌 중 2개 이상 일치하면 동일 관련자이고, 일치 관계는 전이적으로 연결합니다.
      </p>
      <div className="flex flex-col gap-2.5">
        {group.links.map((link) => (
          <div key={`${link.agencyA}-${link.agencyB}`} className="rounded-xl border border-line p-3">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span className="text-sm font-bold">
                {link.agencyA} ↔ {link.agencyB}
              </span>
              <Badge tone={link.related ? 'red' : 'gray'}>{link.matches.length}개 일치</Badge>
              {link.related && !link.relatedInMasterOnly && (
                <Badge tone="orange" title="마스터 등록 값만 비교하면 1개만 일치">
                  입금 계좌 대조로 확인
                </Badge>
              )}
              {!link.related && <Badge tone="gray">그룹 내 전이 연결</Badge>}
            </div>
            <ul className="flex flex-col gap-1.5">
              {link.matches.map((match) => (
                <li key={match.kind} className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px]">
                  <span className="w-14 shrink-0 font-semibold text-ink-soft">{match.kindLabel}</span>
                  <span className="font-medium">{match.left.value}</span>
                  <Badge tone={match.left.basis === 'OBSERVED_DEPOSIT' ? 'orange' : 'gray'}>{match.left.basisLabel}</Badge>
                  <span className="text-ink-mute">↔</span>
                  <Badge tone={match.right.basis === 'OBSERVED_DEPOSIT' ? 'orange' : 'gray'}>{match.right.basisLabel}</Badge>
                  <SourceRef value={match.left.sourceRef} />
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <p className="mt-3 text-xs text-ink-mute">일치 정황은 합산과 소명의 근거이며 부정을 확정하지 않습니다.</p>
    </Card>
  )
}

export function HistoryCard({ history, currentPeriod }: { history: HistoryMonth[]; currentPeriod?: string }) {
  const max = Math.max(...history.map((month) => Math.max(month.totalKrw, month.limitKrw)))
  const bypassed = history.filter((month) => month.exceedsLimit && month.originalApprovers.includes('영업지원팀'))
  return (
    <Card>
      <CardTitle aside={<ProvenanceBadge code="CALCULATED" />}>이 그룹의 월별 합산</CardTitle>
      {bypassed.length > 0 && (
        <div className="mb-3">
          <Notice tone="warn">
            과거 {bypassed.length}개월은 합산이 한도를 넘었는데 대리점별로 따로 심사되어 영업지원팀 전결로 승인됐습니다.
          </Notice>
        </div>
      )}
      <div className="flex flex-col gap-2">
        {history.map((month) => (
          <div key={month.period} className="flex items-center gap-3 text-[13px]">
            <span className={`w-16 shrink-0 font-medium ${month.period === currentPeriod ? 'text-brand-strong' : 'text-ink-soft'}`}>
              {month.period}
            </span>
            <div className="relative h-2 flex-1 rounded-full bg-fill">
              <div
                className={`h-full rounded-full ${month.exceedsLimit ? 'bg-danger' : 'bg-brand'}`}
                style={{ width: `${(month.totalKrw / max) * 100}%` }}
              />
              <div className="absolute -top-1 h-4 w-0.5 bg-ink" style={{ left: `${(month.limitKrw / max) * 100}%` }} />
            </div>
            <span className="num w-24 shrink-0 text-right font-semibold">{won(month.totalKrw)}</span>
            <span className="w-24 shrink-0 text-right text-xs text-ink-mute">
              {month.originalApprovers.length > 0 ? `${month.originalApprovers.join(', ')} 승인` : '이번 정산'}
            </span>
          </div>
        ))}
      </div>
      <p className="mt-3 text-xs text-ink-mute">세로선은 월 한도입니다.</p>
    </Card>
  )
}

function TimelineCard({ review }: { review: Detail }) {
  return (
    <Card>
      <CardTitle aside={<span className="font-mono text-[11px] text-ink-mute">{review.correlationId}</span>}>처리 이력</CardTitle>
      {review.audit.length === 0 ? (
        <Empty>아직 기록이 없습니다.</Empty>
      ) : (
        <ol className="flex flex-col gap-3">
          {review.audit.map((entry) => (
            <li key={entry.id} className="flex gap-3">
              <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand" />
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2 text-[13px]">
                  <span className="font-bold">{ACTION_LABEL[entry.action] ?? entry.action}</span>
                  <span className="text-ink-soft">{ROLE_LABEL[entry.actorRole] ?? entry.actorRole}</span>
                  <ProvenanceBadge code={entry.provenance} />
                </div>
                <p className="mt-0.5 break-words text-xs leading-relaxed text-ink-soft">{entry.detail}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </Card>
  )
}
