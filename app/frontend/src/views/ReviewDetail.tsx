import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type { Group, HistoryMonth, ReviewDetail as Detail, RoleCode } from '../api.ts'
import { AiChecklist, useAiChecklist } from './AiChecklist.tsx'
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
import { ClarificationItem } from './Exceptions.tsx'

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
    <div className="@container">
      <div className="grid grid-cols-1 gap-4 @5xl:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
      <div className="flex min-w-0 flex-col gap-4">
        <DecisionCard key={review.approvalRequestId ?? 0} review={review} role={role} />
        <ClaimsCard review={review} />
      </div>
      <div className="flex min-w-0 flex-col gap-4">
        {review.clarifications.length > 0 && (
          <Card>
            <CardTitle aside={<ProvenanceBadge code="MOCK_REPLY" />}>소명·확인 요청</CardTitle>
            <div className="flex flex-col gap-2.5">
              {review.clarifications.map((item) => (
                <ClarificationItem key={item.id} item={item} compact />
              ))}
            </div>
          </Card>
        )}
        {review.evidence && <EvidenceCard group={review.evidence} />}
        {review.history && <HistoryCard history={review.history} currentPeriod={review.period} />}
        <TimelineCard review={review} />
      </div>
      </div>
    </div>
  )
}

function DecisionCard({ review, role }: { review: Detail; role: RoleCode }) {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  // The latest interpreted reply about this review, for example the explanation behind a resubmission.
  const interpreted = [...review.clarifications]
    .filter((item) => item.subjectType === 'RebateReview' && item.aiSummary)
    .sort((a, b) => b.id - a.id)[0]
  const checklist = useAiChecklist(interpreted)
  const decide = useMutation({
    mutationFn: (approved: boolean) =>
      post(`/approvals/${review.approvalRequestId}/decision`, role, {
        approved,
        comment: [comment.trim(), checklist.note()].filter(Boolean).join(' '),
      }),
    onSuccess: () => queryClient.invalidateQueries(),
  })
  // Sent once: stays locked until the review moves on (the card is keyed by its approval request).
  const locked = decide.isPending || decide.isSuccess

  const over = review.totalKrw - review.limitKrw
  const ratio = Math.min(100, Math.round((review.limitKrw / Math.max(review.totalKrw, review.limitKrw)) * 100))
  const fill = Math.min(100, Math.round((review.totalKrw / Math.max(review.totalKrw, review.limitKrw)) * 100))
  const pending = review.approvalStatus === 'PENDING'
  const hasAuthority = role === review.requiredApprover

  return (
    <Card className="@container">
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <StatusBadge status={review.status} />
        <Badge tone="gray">{review.period}</Badge>
        {review.relatedGroup ? <Badge tone="blue">관련자 그룹 합산</Badge> : <Badge tone="gray">단독</Badge>}
        {review.round > 1 && <Badge tone="violet">{review.round}차 상신 · 소명 첨부</Badge>}
        <span className="text-xs text-ink-mute">검토 #{review.id}</span>
      </div>
      <h2 className="text-xl font-bold leading-snug">
        {review.relatedGroup ? `${review.unitKey} · ${review.memberAgencyIds.split(',').join(', ')}` : review.unitKey}
      </h2>

      <div className="mt-5 grid grid-cols-1 gap-3 @md:grid-cols-3">
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
        <div className="absolute -top-1 h-4.5 w-0.5 bg-ink" style={{ left: `calc(${ratio}% - 2px)` }} title="월 한도" />
      </div>
      <p className="mt-3 text-[13px] leading-relaxed text-ink-soft">
        승인 권한은 <b className="text-ink">{ROLE_LABEL[review.requiredApprover]}</b>입니다.{' '}
        {review.exceedsLimit
          ? '합산 금액이 월 한도를 넘어 영업지원팀 전결로 처리할 수 없습니다.'
          : '월 한도 이내라 영업지원팀 전결 사안입니다.'}
      </p>

      {STATUS_NOTICE[review.status] && (
        <div className="mt-3">
          <Notice tone={review.status === 'REFERRED_TO_AUDIT' ? 'error' : 'warn'}>{STATUS_NOTICE[review.status]}</Notice>
        </div>
      )}

      <div className="mt-5 border-t border-line pt-4">
        {pending ? (
          <div className="flex flex-col gap-3">
            {!hasAuthority && (
              <Notice tone="warn">
                현재 역할은 {ROLE_LABEL[role]}입니다. 이 건은 {ROLE_LABEL[review.requiredApprover]} 권한 사안이라 결정을
                보내면 서버가 거부합니다.
              </Notice>
            )}
            <AiChecklist checklist={checklist} />
            <input
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              placeholder="결정 사유 (선택)"
              className="h-10 rounded-xl border border-line px-3.5 text-sm outline-none focus:border-brand"
            />
            <div className="flex gap-2">
              <Button size="lg" className="flex-1" disabled={locked} onClick={() => decide.mutate(true)}>
                승인
              </Button>
              <Button kind="danger" size="lg" className="flex-1" disabled={locked} onClick={() => decide.mutate(false)}>
                반려
              </Button>
            </div>
            {decide.isSuccess && <Notice tone="info">결정을 보냈습니다. 반영되는 중입니다…</Notice>}
            {review.relatedGroup && review.round === 1 && (
              <p className="text-xs leading-relaxed text-ink-mute">
                관련자 그룹을 반려하면 바로 확정하지 않고 구성 대리점에 관계 소명을 요청합니다. 소명이 오면 다시 상신되고, 다시
                반려하거나 회신이 없으면 감사로 넘깁니다.
              </p>
            )}
            {review.relatedGroup && review.round > 1 && (
              <p className="text-xs leading-relaxed text-ink-mute">소명을 본 뒤에도 반려하면 감사로 넘기고 장려금은 보류합니다.</p>
            )}
            {decide.error && <Notice tone="error">{decide.error.message}</Notice>}
          </div>
        ) : review.approvalStatus ? (
          <div className="flex flex-wrap items-center gap-2 text-sm">
            {review.approvalStatus === 'APPROVED' ? (
              <Badge tone="blue">승인</Badge>
            ) : review.approvalStatus === 'WITHDRAWN' ? (
              <Badge tone="gray">상신 철회</Badge>
            ) : (
              <Badge tone="red">반려</Badge>
            )}
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

const STATUS_NOTICE: Record<string, string> = {
  CLARIFICATION_REQUESTED:
    '반려 후 관계 소명을 요청했습니다. 장려금은 지급하지 않고 회신을 기다립니다. 회신이 오면 소명을 첨부해 다시 상신합니다.',
  REFERRED_TO_AUDIT: '감사로 넘겼습니다. 부정을 확정한 것은 아니며, 장려금은 감사 확인 전까지 보류합니다.',
  SUPERSEDED: '마스터 정정으로 한도 단위가 바뀌어 이 검토는 대체되었습니다. 승인 요청은 철회되고 새 검토로 다시 상신했습니다.',
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
    <div className="@container min-w-0 rounded-xl bg-fill px-4 py-3.5">
      <div className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
        <span className="whitespace-nowrap text-xs font-medium text-ink-soft">{label}</span>
        <span className="shrink-0">{tag}</span>
      </div>
      <div className={`num mt-1.5 whitespace-nowrap text-lg font-bold @[11rem]:text-xl @[13rem]:text-[22px] ${color}`}>
        {value}
      </div>
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
            <ul className="flex flex-col gap-2.5">
              {link.matches.map((match) => {
                const sources = [...new Set([match.left.sourceRef, match.right.sourceRef])]
                return (
                  <li key={match.kind} className="grid grid-cols-[3.5rem_minmax(0,1fr)] gap-x-2 text-[13px]">
                    <span className="pt-px font-semibold text-ink-soft">{match.kindLabel}</span>
                    <div className="min-w-0">
                      <div className="break-words font-medium">{match.left.value}</div>
                      <div className="mt-1 flex flex-wrap items-center gap-1.5">
                        <Badge tone={match.left.basis === 'OBSERVED_DEPOSIT' ? 'orange' : 'gray'}>
                          {match.left.agencyId} {match.left.basisLabel}
                        </Badge>
                        <span className="text-ink-mute">↔</span>
                        <Badge tone={match.right.basis === 'OBSERVED_DEPOSIT' ? 'orange' : 'gray'}>
                          {match.right.agencyId} {match.right.basisLabel}
                        </Badge>
                        {sources.map((source) => (
                          <SourceRef key={source} value={source} />
                        ))}
                      </div>
                    </div>
                  </li>
                )
              })}
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
    <Card className="@container">
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
            <span className="num w-[5.5rem] shrink-0 whitespace-nowrap text-right font-semibold">{won(month.totalKrw)}</span>
            <span className="hidden w-24 shrink-0 whitespace-nowrap text-right text-xs text-ink-mute @sm:inline">
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
