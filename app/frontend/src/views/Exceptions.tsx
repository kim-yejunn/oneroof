import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type {
  Clarification,
  ClarificationOutcome,
  Finding,
  Identity,
  IdentityKindCode,
  MasterChange,
  Referral,
  RelatedParties,
  RoleCode,
} from '../api.ts'
import {
  Badge,
  Button,
  Card,
  CardTitle,
  Empty,
  KIND_LABEL,
  Notice,
  OUTCOME_LABEL,
  ProvenanceBadge,
  ROLE_LABEL,
  SourceRef,
  StatusBadge,
  won,
} from '../ui.tsx'

/**
 * Where conditions change and exceptions are handled: master corrections, the questions sent to
 * agencies and their (simulated) answers, months found settled under the wrong authority, and
 * matters handed to audit.
 */
export function Exceptions({ role }: { role: RoleCode }) {
  return (
    <div className="@container h-full overflow-y-auto pb-6">
      <div className="grid grid-cols-1 gap-4 @5xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-4">
          <MasterChangeForm role={role} />
          <ClarificationsCard />
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          <FindingsCard />
          <ReferralsCard />
          <ChangeHistoryCard />
        </div>
      </div>
    </div>
  )
}

const KINDS: IdentityKindCode[] = ['REPRESENTATIVE', 'ADDRESS', 'BANK_ACCOUNT']

const EXAMPLE = {
  agencyId: 'D-041',
  kind: 'ADDRESS' as IdentityKindCode,
  newValue: '대전시 중앙로 260',
  effectiveFrom: '2026-01-01',
  reason: '2025-12 사업장 이전 신고서 접수, 마스터 미반영 확인',
}

function MasterChangeForm({ role }: { role: RoleCode }) {
  const queryClient = useQueryClient()
  const [agencyId, setAgencyId] = useState('D-041')
  const [kind, setKind] = useState<IdentityKindCode>('ADDRESS')
  const [newValue, setNewValue] = useState('')
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [reason, setReason] = useState('')
  const { data: related } = useQuery({
    queryKey: ['related-parties'],
    queryFn: () => get<RelatedParties>('/related-parties'),
    refetchInterval: false,
  })
  const { data: identities = [] } = useQuery({
    queryKey: ['identities', agencyId],
    queryFn: () => get<Identity[]>(`/agencies/${agencyId}/identities`),
  })
  const report = useMutation({
    mutationFn: () => post<{ accepted: boolean }>('/master-changes', role, { agencyId, kind, newValue, effectiveFrom, reason }),
    onSuccess: async () => {
      await queryClient.invalidateQueries()
      setNewValue('')
      setReason('')
    },
  })
  const fillExample = () => {
    setAgencyId(EXAMPLE.agencyId)
    setKind(EXAMPLE.kind)
    setNewValue(EXAMPLE.newValue)
    setEffectiveFrom(EXAMPLE.effectiveFrom)
    setReason(EXAMPLE.reason)
  }
  const current = identities.filter((identity) => identity.effectiveTo === null)

  return (
    <Card>
      <CardTitle
        aside={
          <Button size="sm" kind="ghost" onClick={fillExample}>
            시연 예시 채우기
          </Button>
        }
      >
        마스터 정정 신고
      </CardTitle>
      <p className="mb-3 text-[13px] leading-relaxed text-ink-soft">
        대표자·주소·정산계좌가 바뀌면 관련자 그룹을 다시 계산합니다. 아직 결정 전인 검토는 새 한도 단위로 다시 상신하고,
        전결로 끝난 달이 임원 승인 대상이었다면 사후 확인을 요청합니다. 신고 내용은 <ProvenanceBadge code="DEPARTMENT_CLAIM" />
        으로 기록합니다.
      </p>
      <div className="grid grid-cols-1 gap-2.5 @md:grid-cols-2">
        <Field label="대리점">
          <select value={agencyId} onChange={(event) => setAgencyId(event.target.value)} className={INPUT}>
            {(related?.agencies ?? [{ agencyId, name: '' }]).map((agency) => (
              <option key={agency.agencyId} value={agency.agencyId}>
                {agency.agencyId} {agency.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="정정할 식별정보">
          <select value={kind} onChange={(event) => setKind(event.target.value as IdentityKindCode)} className={INPUT}>
            {KINDS.map((item) => (
              <option key={item} value={item}>
                {KIND_LABEL[item]}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="mt-2.5 rounded-xl bg-fill px-3.5 py-2.5 text-[13px]">
        <span className="text-ink-soft">현재 등록 값 </span>
        {current.length === 0 ? (
          <span className="text-ink-mute">—</span>
        ) : (
          <ul className="mt-1 flex flex-col gap-1">
            {current.map((identity) => (
              <li key={identity.kind} className={`flex flex-wrap items-center gap-1.5 ${identity.kind === kind ? 'font-semibold' : 'text-ink-soft'}`}>
                <span className="w-14 shrink-0 text-xs text-ink-mute">{KIND_LABEL[identity.kind]}</span>
                {identity.identityValue}
                <span className="text-xs text-ink-mute">({identity.effectiveFrom} 부터)</span>
                <ProvenanceBadge code={identity.provenance} />
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="mt-2.5 grid grid-cols-1 gap-2.5 @md:grid-cols-[minmax(0,1fr)_10rem]">
        <Field label="새 값">
          <input value={newValue} onChange={(event) => setNewValue(event.target.value)} className={INPUT} placeholder="예: 대전시 중앙로 260" />
        </Field>
        <Field label="적용일">
          <input type="date" value={effectiveFrom} max="2026-07-01" onChange={(event) => setEffectiveFrom(event.target.value)} className={INPUT} />
        </Field>
      </div>
      <div className="mt-2.5">
        <Field label="정정 사유">
          <input value={reason} onChange={(event) => setReason(event.target.value)} className={INPUT} placeholder="근거 서류와 함께" />
        </Field>
      </div>
      <Button
        className="mt-3 w-full"
        disabled={report.isPending || !newValue || !effectiveFrom || !reason}
        onClick={() => report.mutate()}
      >
        정정 신고 접수
      </Button>
      {report.error && <div className="mt-2"><Notice tone="error">{report.error.message}</Notice></div>}
      {report.data && (
        <div className="mt-2">
          <Notice tone="info">
            {report.data.accepted ? '접수했습니다. 그룹 재계산 결과는 처리할 일과 오른쪽 소급 발견에 이어서 나타납니다.' : '같은 정정이 이미 접수되어 다시 처리하지 않았습니다.'}
          </Notice>
        </div>
      )}
    </Card>
  )
}

const INPUT = 'h-10 w-full rounded-xl border border-line bg-white px-3 text-sm outline-none focus:border-brand'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex min-w-0 flex-col gap-1">
      <span className="text-xs font-medium text-ink-soft">{label}</span>
      {children}
    </label>
  )
}

function ClarificationsCard() {
  const { data: clarifications = [] } = useQuery({
    queryKey: ['clarifications'],
    queryFn: () => get<Clarification[]>('/clarifications'),
  })
  const open = clarifications.filter((item) => item.status === 'SENT')
  const closed = clarifications.filter((item) => item.status !== 'SENT')
  return (
    <Card>
      <CardTitle aside={<ProvenanceBadge code="MOCK_REPLY" />}>대리점 소명·확인 요청</CardTitle>
      <p className="mb-3 text-[13px] leading-relaxed text-ink-soft">
        발송은 모의 처리이고, 대리점 회신은 여기서 대신 입력합니다. 회신 기한 7일은 원본에 규정이 없어 둔{' '}
        <ProvenanceBadge code="ASSUMPTION" />
        입니다.
      </p>
      {clarifications.length === 0 ? (
        <Empty>보낸 요청이 없습니다. 지급 계좌가 확인되지 않거나 관련자 그룹이 반려되면 요청이 생깁니다.</Empty>
      ) : (
        <div className="flex flex-col gap-3">
          {open.map((item) => (
            <ClarificationItem key={item.id} item={item} />
          ))}
          {closed.map((item) => (
            <ClarificationItem key={item.id} item={item} />
          ))}
        </div>
      )}
    </Card>
  )
}

const SAMPLE_REPLY: Record<string, string> = {
  RELATION_EXPLANATION: '세 법인은 대표가 같은 별도 법인으로 성남 사무실을 함께 임차함. 임대차계약서·법인등기부 첨부',
  ACCOUNT_CONFIRMATION: '등록된 정산계좌가 맞음. 다른 계좌 입금은 대표자 개인 계좌로 대납한 것',
  OFFSET_NOTICE: '5월 반품 2건(1,200,000원) 정산이 미수금에 반영되지 않음. 반품 확인서 첨부',
}

export function ClarificationItem({ item, compact = false }: { item: Clarification; compact?: boolean }) {
  const queryClient = useQueryClient()
  const [text, setText] = useState(SAMPLE_REPLY[item.kind] ?? '')
  const [account, setAccount] = useState('')
  const [expanded, setExpanded] = useState(false)
  const reply = useMutation({
    mutationFn: (outcome: ClarificationOutcome) =>
      post(`/clarifications/${item.id}/reply`, 'SALES_SUPPORT', {
        outcome,
        replyText: outcome === 'NO_REPLY' || outcome === 'OFFSET_ACCEPTED' ? '' : text,
        replyAccount: outcome === 'ACCOUNT_CHANGED' ? account : null,
      }),
    onSuccess: () => queryClient.invalidateQueries(),
  })
  const waiting = item.status === 'SENT' && !reply.isSuccess

  return (
    <div className={`rounded-xl border p-3.5 ${waiting ? 'border-violet/40' : 'border-line'}`}>
      <div className="flex flex-wrap items-center gap-1.5">
        {/* SENT means something else for payments ("은행 회신 대기"), so the request says it in its own words. */}
        {waiting ? <Badge tone="orange">회신 대기</Badge> : <StatusBadge status={item.status} />}
        <Badge tone="violet">{KIND_LABEL[item.kind]}</Badge>
        <span className="text-xs text-ink-mute">
          요청 #{item.id} · {item.agencyIds.split(',').join(', ')} · 기한 {item.dueOn}
        </span>
      </div>
      <p className="mt-1.5 text-sm font-semibold">{item.title}</p>
      {(!compact || expanded) && (
        <p className="mt-1.5 whitespace-pre-line rounded-lg bg-fill px-3 py-2 text-xs leading-relaxed text-ink-soft">{item.question}</p>
      )}
      {compact && (
        <button type="button" className="mt-1 text-xs font-semibold text-brand-strong" onClick={() => setExpanded(!expanded)}>
          {expanded ? '요청 전문 접기' : '요청 전문 보기'}
        </button>
      )}
      {item.outcome && (
        <div className="mt-2 flex flex-wrap items-center gap-1.5 text-[13px]">
          <Badge tone={item.outcome === 'NO_REPLY' ? 'red' : 'teal'}>{OUTCOME_LABEL[item.outcome]}</Badge>
          {item.replyAccount && <span className="font-semibold">{item.replyAccount}</span>}
          {item.replyText && <span className="text-ink-soft">“{item.replyText}”</span>}
          <ProvenanceBadge code="MOCK_REPLY" />
        </div>
      )}
      {item.aiSummary && <Interpretation item={item} />}
      {item.outcome && item.outcome !== 'NO_REPLY' && !item.aiSummary && (
        <p className="mt-2 text-xs text-ink-mute">회신 해석 중…</p>
      )}
      {reply.isSuccess && item.status === 'SENT' && (
        <p className="mt-2 text-xs font-semibold text-brand-strong">회신을 보냈습니다. 반영되는 중입니다…</p>
      )}
      {waiting && (
        <div className="mt-3 flex flex-col gap-2 border-t border-line pt-3">
          <span className="text-xs font-semibold text-ink-soft">대리점 회신 입력 (모의)</span>
          <textarea
            value={text}
            onChange={(event) => setText(event.target.value)}
            rows={2}
            className="w-full resize-none rounded-xl border border-line px-3 py-2 text-[13px] outline-none focus:border-brand"
          />
          {item.kind === 'ACCOUNT_CONFIRMATION' && (
            <input
              value={account}
              onChange={(event) => setAccount(event.target.value)}
              placeholder="다른 계좌로 받을 때만: 예) 국민 412-27-556310"
              className="h-9 rounded-xl border border-line px-3 text-[13px] outline-none focus:border-brand"
            />
          )}
          <div className="flex flex-wrap gap-1.5">
            {item.kind === 'OFFSET_NOTICE' ? (
              <>
                <Button size="sm" kind="secondary" disabled={reply.isPending} onClick={() => reply.mutate('OFFSET_ACCEPTED')}>
                  상계 수용
                </Button>
                <Button size="sm" kind="secondary" disabled={reply.isPending || !text} onClick={() => reply.mutate('OFFSET_DISPUTED')}>
                  이의 제기
                </Button>
              </>
            ) : item.kind === 'RELATION_EXPLANATION' ? (
              <Button size="sm" kind="secondary" disabled={reply.isPending || !text} onClick={() => reply.mutate('EXPLAINED')}>
                소명 제출
              </Button>
            ) : (
              <>
                <Button size="sm" kind="secondary" disabled={reply.isPending} onClick={() => reply.mutate('ACCOUNT_CONFIRMED')}>
                  등록 계좌 확인
                </Button>
                <Button size="sm" kind="secondary" disabled={reply.isPending || !account} onClick={() => reply.mutate('ACCOUNT_CHANGED')}>
                  다른 계좌로 변경
                </Button>
              </>
            )}
            <Button size="sm" kind="danger" disabled={reply.isPending} onClick={() => reply.mutate('NO_REPLY')}>
              {item.kind === 'OFFSET_NOTICE' ? '기한 경과(이의 없음)' : '기한 경과(무응답)'}
            </Button>
          </div>
          {reply.error && <Notice tone="error">{reply.error.message}</Notice>}
        </div>
      )}
    </div>
  )
}

const STANCE_LABEL: Record<string, string> = {
  EXPLAINS_RELATION: '관계 사유 설명',
  DENIES_RELATION: '관계 부인',
  PARTIAL: '일부만 설명',
  UNCLEAR: '불분명',
  CONFIRMS_ACCOUNT: '등록 계좌 확인',
  CHANGES_ACCOUNT: '계좌 변경 요청',
  ACCEPTS_OFFSET: '상계 수용',
  DISPUTES_OFFSET: '상계 이의',
}

const lines = (value: string | null) => (value ? value.split('\n').filter(Boolean) : [])

/** The reply put into shape for the reviewer. Advisory only: nothing in the flow reads it. */
function Interpretation({ item }: { item: Clarification }) {
  const ai = item.aiInterpreter !== 'rule-based'
  const evidence = lines(item.aiEvidence)
  const amounts = lines(item.aiAmounts)
  const gaps = lines(item.aiGaps)
  const followUps = lines(item.aiFollowUps)
  const notes = lines(item.aiNotes)
  return (
    <div className="mt-2.5 rounded-lg border border-violet/30 bg-violet-soft/40 px-3 py-2.5 text-[13px]">
      <div className="mb-1.5 flex flex-wrap items-center gap-1.5">
        <span className="text-xs font-bold text-violet">회신 해석 · 검토용</span>
        <ProvenanceBadge code={ai ? 'AI_GENERATED' : 'CALCULATED'} />
        <Badge tone="gray">{ai ? item.aiInterpreter : item.aiFellBack ? '규칙 기반 (AI 실패로 대체)' : '규칙 기반'}</Badge>
        {item.aiStance && <Badge tone="violet">{STANCE_LABEL[item.aiStance] ?? item.aiStance}</Badge>}
      </div>
      <p className="leading-relaxed">{item.aiSummary}</p>
      <dl className="mt-1.5 grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs leading-relaxed">
        {evidence.length > 0 && (
          <>
            <dt className="font-semibold text-ink-soft">언급한 증빙</dt>
            <dd>{evidence.join(', ')}</dd>
          </>
        )}
        {amounts.length > 0 && (
          <>
            <dt className="font-semibold text-ink-soft">적힌 금액</dt>
            <dd className="num">{amounts.map((amount) => won(Number(amount))).join(', ')}</dd>
          </>
        )}
        {gaps.length > 0 && (
          <>
            <dt className="font-semibold text-danger">답하지 않은 것</dt>
            <dd>{gaps.join(' · ')}</dd>
          </>
        )}
        {followUps.length > 0 && (
          <>
            <dt className="font-semibold text-ink-soft">확인할 일</dt>
            <dd>{followUps.join(' · ')}</dd>
          </>
        )}
      </dl>
      {notes.length > 0 && <p className="mt-1.5 text-[11px] text-ink-mute">검증: {notes.join(' / ')}</p>}
      <p className="mt-1.5 text-[11px] text-ink-mute">
        회신 원문을 구조화한 참고 자료입니다. 금액·증빙은 원문에 있는 것만 남겼습니다. 결정은 사람이 하고, 결정 화면에서 확인한 제안은
        결정 기록에 남습니다.
      </p>
    </div>
  )
}

function FindingsCard() {
  const { data: findings = [] } = useQuery({ queryKey: ['findings'], queryFn: () => get<Finding[]>('/findings') })
  return (
    <Card>
      <CardTitle aside={<ProvenanceBadge code="CALCULATED" />}>소급 발견 · 전결 처리분 사후 확인</CardTitle>
      <p className="mb-3 text-[13px] leading-relaxed text-ink-soft">
        마스터 정정으로 관련자 그룹이 생기거나 커지면, 지난달 중 그룹 합산이 한도를 넘었는데 영업지원팀 전결로 처리된 달을
        찾습니다. 지급은 되돌리지 않고 영업임원이 사후 확인하거나 감사로 넘깁니다.
      </p>
      {findings.length === 0 ? (
        <Empty>소급 발견이 없습니다.</Empty>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-line text-left text-xs text-ink-mute">
              <th className="py-2 font-medium">달</th>
              <th className="py-2 font-medium">그룹</th>
              <th className="py-2 text-right font-medium">합산</th>
              <th className="py-2 pl-3 font-medium">당시 승인</th>
              <th className="py-2 font-medium">상태</th>
            </tr>
          </thead>
          <tbody>
            {findings.map((finding) => (
              <tr key={finding.id} className="border-b border-line align-top last:border-0" title={finding.rationale}>
                <td className="py-2.5 font-semibold">{finding.period}</td>
                <td className="py-2.5">
                  <div className="font-semibold">{finding.unitKey}</div>
                  <div className="text-xs text-ink-mute">{finding.memberAgencyIds.split(',').join(', ')}</div>
                </td>
                <td className="num py-2.5 text-right font-semibold text-danger">{won(finding.totalKrw)}</td>
                <td className="py-2.5 pl-3 text-xs text-ink-soft">
                  {finding.approvedBy} 전결
                  <div className="text-ink-mute">필요: {ROLE_LABEL[finding.requiredApprover]}</div>
                </td>
                <td className="py-2.5">
                  <StatusBadge status={finding.status} />
                  {finding.decisionComment && <div className="mt-1 text-xs text-ink-mute">“{finding.decisionComment}”</div>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  )
}

function ReferralsCard() {
  const { data: referrals = [] } = useQuery({ queryKey: ['audit-referrals'], queryFn: () => get<Referral[]>('/audit-referrals') })
  return (
    <Card>
      <CardTitle aside={<span className="text-xs text-ink-mute">{referrals.length}건</span>}>감사 이관</CardTitle>
      <p className="mb-3 text-[13px] leading-relaxed text-ink-soft">
        이관은 이번 검토 회차를 닫을 뿐 부정을 확정하지 않습니다. 해당 장려금은 지급하지 않고 보류한 채 후속 확인을 기다립니다.
      </p>
      {referrals.length === 0 ? (
        <Empty>감사로 넘긴 건이 없습니다.</Empty>
      ) : (
        <ul className="flex flex-col gap-2.5">
          {referrals.map((referral) => (
            <li key={referral.id} className="rounded-xl border border-line p-3">
              <div className="flex flex-wrap items-center gap-1.5">
                <StatusBadge status={referral.status} />
                <span className="text-xs text-ink-mute">
                  이관 #{referral.id} · {referral.referredOn} · {referral.subjectType} #{referral.subjectId}
                </span>
              </div>
              <p className="mt-1 text-sm font-semibold">{referral.title}</p>
              <p className="mt-0.5 text-xs leading-relaxed text-ink-soft">{referral.reason}</p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function ChangeHistoryCard() {
  const { data: changes = [] } = useQuery({ queryKey: ['master-changes'], queryFn: () => get<MasterChange[]>('/master-changes') })
  return (
    <Card>
      <CardTitle aside={<span className="text-xs text-ink-mute">{changes.length}건</span>}>마스터 정정 이력</CardTitle>
      {changes.length === 0 ? (
        <Empty>정정 이력이 없습니다. 원본 마스터 값 그대로입니다.</Empty>
      ) : (
        <ul className="flex flex-col gap-2.5">
          {changes.map((change) => (
            <li key={change.id} className="rounded-xl border border-line p-3 text-[13px]">
              <div className="flex flex-wrap items-center gap-1.5">
                <span className="font-bold">
                  #{change.id} {change.agencyId} {KIND_LABEL[change.kind]}
                </span>
                <ProvenanceBadge code={change.provenance} />
                <span className="text-xs text-ink-mute">{change.effectiveFrom} 부터</span>
              </div>
              <div className="mt-1 break-words">
                <span className="text-ink-mute line-through">{change.previousValue}</span> → <b>{change.newValue}</b>
              </div>
              <div className="mt-0.5 text-xs text-ink-soft">
                {change.reason} · {ROLE_LABEL[change.reportedBy] ?? change.reportedBy}
              </div>
              <SourceRef value={`master_change#${change.id}`} />
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
