import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type { Clarification, Finding, Payment, PendingApproval, RoleCode } from '../api.ts'
import { Badge, Button, Card, CardTitle, Notice, ProvenanceBadge } from '../ui.tsx'

type Step = {
  id: string
  title: string
  role: RoleCode | 'AGENCY' | 'BANK'
  note: string
  run: () => Promise<string>
}

const pending = (role: RoleCode) => get<PendingApproval[]>(`/approvals/pending?role=${role}`)

/** Polls until the background steps have produced what the next step needs. */
async function until<T>(read: () => Promise<T>, done: (value: T) => boolean, what: string): Promise<T> {
  const started = Date.now()
  while (Date.now() - started < 60_000) {
    const value = await read()
    if (done(value)) {
      return value
    }
    await new Promise((resolve) => setTimeout(resolve, 800))
  }
  throw new Error(`${what}: 60초 안에 반영되지 않았습니다`)
}

const decideAll = async (role: RoleCode, items: PendingApproval[], approved: boolean, comment: string) =>
  post('/approvals/decisions', role, { ids: items.map((item) => item.id), approved, comment })

const STEPS: { title: string; tone: 'blue' | 'orange' | 'red'; steps: Step[] }[] = [
  {
    title: '정상 흐름',
    tone: 'blue',
    steps: [
      {
        id: 'n1',
        title: '6월 정산 접수',
        role: 'SALES_SUPPORT',
        note: '미지급 40건을 한도 단위로 묶어 상신',
        run: async () => {
          await post('/settlements', 'SALES_SUPPORT', { period: '2026-06' })
          const routine = await until(() => pending('SALES_SUPPORT'), (items) => items.length > 0, '상신')
          return `영업지원팀 승인 대기 ${routine.length}건`
        },
      },
      {
        id: 'n2',
        title: '한도 이내 전결 일괄 승인',
        role: 'SALES_SUPPORT',
        note: '90일 초과 연체 대리점은 회계·자금팀 상계 검토로 이동',
        run: async () => {
          const routine = (await pending('SALES_SUPPORT')).filter((item) => item.exceedsLimit === false)
          await decideAll('SALES_SUPPORT', routine, true, '한도 이내 전결')
          const offsets = await until(() => pending('FINANCE'), (items) => items.length > 0, '상계 상신')
          return `전결 ${routine.length}건 승인, 상계 검토 ${offsets.length}건`
        },
      },
      {
        id: 'n3',
        title: '상계 제안 일괄 결정',
        role: 'FINANCE',
        note: '연체 미수금과 대등액 상계, 대리점에 상계 통지',
        run: async () => {
          const offsets = (await pending('FINANCE')).filter((item) => item.subjectType === 'OffsetProposal')
          await decideAll('FINANCE', offsets, true, '계약 조항에 따라 상계')
          await until(() => get<Payment[]>('/payments?status=OFFSET_REVIEW'), (items) => items.length === 0, '상계 반영')
          return `상계 ${offsets.length}건 결정`
        },
      },
      {
        id: 'n4',
        title: '은행 이체 완료 회신',
        role: 'BANK',
        note: '모의 은행이 대기 중인 이체 전체에 완료 회신',
        run: async () => {
          const result = await post<{ replied: number }>('/payments/bank-replies', 'SALES_SUPPORT', {})
          return `이체 완료 회신 ${result.replied}건`
        },
      },
    ],
  },
  {
    title: '조건 변경',
    tone: 'orange',
    steps: [
      {
        id: 'c1',
        title: 'D-034 연체 일부 입금 (상계 결정 전에)',
        role: 'FINANCE',
        note: '8,000,000원 입금 → 상계 제안이 1,038,360원으로 다시 상신',
        run: async () => {
          await post('/agencies/D-034/deposits', 'FINANCE', { amountKrw: 8_000_000, bankAccount: '모의 입금 계좌' })
          return 'D-034 입금 반영, 상계 제안 갱신'
        },
      },
      {
        id: 'c2',
        title: 'D-041 주소 정정 신고',
        role: 'SALES_SUPPORT',
        note: 'D-034와 주소가 같아져 관련자 그룹이 생기고 지난달 전결분 사후 확인',
        run: async () => {
          await post('/master-changes', 'SALES_SUPPORT', {
            agencyId: 'D-041',
            kind: 'ADDRESS',
            newValue: '대전시 중앙로 260',
            effectiveFrom: '2026-01-01',
            reason: '2025-12 사업장 이전 신고서 접수, 마스터 미반영 확인',
          })
          const findings = await until(() => get<Finding[]>('/findings'), (items) => items.length > 0, '소급 발견')
          return `소급 발견 ${findings.length}건, 영업임원 사후 확인 요청`
        },
      },
    ],
  },
  {
    title: '승인 거절 · 정보 부족',
    tone: 'red',
    steps: [
      {
        id: 'e1',
        title: '영업임원이 관련자 그룹 반려',
        role: 'SALES_EXECUTIVE',
        note: '확정 반려가 아니라 구성 대리점에 관계 소명 요청',
        run: async () => {
          const group = (await pending('SALES_EXECUTIVE')).filter((item) => item.subjectType === 'RebateReview')
          if (group.length === 0) {
            throw new Error('영업임원 승인 대기 검토가 없습니다')
          }
          await decideAll('SALES_EXECUTIVE', group.slice(0, 1), false, '주소·계좌 공유 사유 확인 필요')
          await until(
            () => get<Clarification[]>('/clarifications?status=SENT'),
            (items) => items.some((item) => item.kind === 'RELATION_EXPLANATION'),
            '소명 요청',
          )
          return '관계 소명 요청 발송'
        },
      },
      {
        id: 'e2',
        title: '대리점 소명 회신 (모의) → AI 해석',
        role: 'AGENCY',
        note: '회신이 첨부된 2차 상신과 회신 해석이 생김',
        run: async () => {
          const request = (await get<Clarification[]>('/clarifications?status=SENT')).find(
            (item) => item.kind === 'RELATION_EXPLANATION',
          )
          if (!request) {
            throw new Error('열린 관계 소명 요청이 없습니다')
          }
          await post(`/clarifications/${request.id}/reply`, 'SALES_SUPPORT', {
            outcome: 'EXPLAINED',
            replyText:
              '세 법인은 대표가 같은 별도 법인입니다. D-011과 D-024는 성남 사무실을 함께 임차하고 있어 주소가 같습니다. 임대차계약서와 법인등기부를 첨부합니다.',
          })
          return '소명 회신 입력, 영업임원에게 재상신'
        },
      },
      {
        id: 'e3',
        title: 'D-012 상계 이의 (상계 결정 뒤에)',
        role: 'AGENCY',
        note: '회계·자금팀에 재검토 상신, 회신은 AI가 해석',
        run: async () => {
          const notice = (await get<Clarification[]>('/clarifications?status=SENT')).find(
            (item) => item.kind === 'OFFSET_NOTICE' && item.agencyIds === 'D-012',
          )
          if (!notice) {
            throw new Error('D-012 상계 통지가 없습니다. 정상 흐름의 상계 결정을 먼저 하세요')
          }
          await post(`/clarifications/${notice.id}/reply`, 'SALES_SUPPORT', {
            outcome: 'OFFSET_DISPUTED',
            replyText: '5월에 반품한 2건(1,200,000원)이 미수금에서 차감되지 않았습니다. 반품 확인서를 첨부합니다.',
          })
          return 'D-012 이의 접수, 회계·자금팀 재검토 대기'
        },
      },
    ],
  },
]

const ROLE_TEXT: Record<Step['role'], string> = {
  SALES_SUPPORT: '영업지원팀',
  SALES_EXECUTIVE: '영업임원',
  FINANCE: '회계·자금팀',
  AGENCY: '대리점(모의)',
  BANK: '은행(모의)',
}

/**
 * Rehearsal helper: puts the demo back to the original data and runs the usual steps through the
 * same API the screens use. Every step can equally be done by hand on its own screen.
 */
export function DemoGuide({ role }: { role: RoleCode }) {
  const queryClient = useQueryClient()
  const [running, setRunning] = useState<string | null>(null)
  const [results, setResults] = useState<Record<string, { ok: boolean; text: string }>>({})

  const run = async (id: string, action: () => Promise<string>) => {
    setRunning(id)
    try {
      const text = await action()
      setResults((previous) => ({ ...previous, [id]: { ok: true, text } }))
    } catch (error) {
      setResults((previous) => ({ ...previous, [id]: { ok: false, text: (error as Error).message } }))
    } finally {
      await queryClient.invalidateQueries()
      setRunning(null)
    }
  }

  const reset = () => {
    if (!window.confirm('모든 처리 기록을 지우고 원본 데이터만 남깁니다. 계속할까요?')) {
      return
    }
    void run('reset', async () => {
      const result = await post<{ tablesCleared: number }>('/demo/reset', role, {})
      setResults({})
      return `테이블 ${result.tablesCleared}개를 비우고 원본을 다시 적재했습니다`
    })
  }

  return (
    <div className="flex h-full flex-col gap-4 overflow-y-auto pb-6">
      <Card>
        <CardTitle aside={<ProvenanceBadge code="ORIGINAL" />}>시연 도우미</CardTitle>
        <p className="text-[13px] leading-relaxed text-ink-soft">
          연습·녹화용입니다. 초기화하면 처리 기록이 모두 지워지고 원본 데이터만 다시 적재됩니다(원본 파일은 바뀌지 않음). 아래 단계는
          화면의 버튼과 같은 API를 순서대로 부르며, 각 화면에서 직접 눌러도 결과가 같습니다.
        </p>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <Button kind="danger" disabled={running !== null} onClick={reset}>
            {running === 'reset' ? '초기화 중…' : '시연 초기화'}
          </Button>
          {results.reset && <ResultText result={results.reset} />}
        </div>
      </Card>
      <div className="@container">
        <div className="grid grid-cols-1 gap-4 @5xl:grid-cols-3">
          {STEPS.map((group) => (
            <Card key={group.title} className="min-w-0">
              <CardTitle aside={<Badge tone={group.tone}>{group.steps.length}단계</Badge>}>{group.title}</CardTitle>
              <ol className="flex flex-col gap-2.5">
                {group.steps.map((step, index) => (
                  <li key={step.id} className="rounded-xl bg-fill px-3.5 py-3">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="text-[13px] font-semibold">
                          {index + 1}. {step.title}
                        </div>
                        <div className="mt-0.5 text-xs text-ink-mute">
                          {ROLE_TEXT[step.role]} · {step.note}
                        </div>
                      </div>
                      <Button size="sm" kind="secondary" disabled={running !== null} onClick={() => void run(step.id, step.run)}>
                        {running === step.id ? '진행 중…' : '실행'}
                      </Button>
                    </div>
                    {results[step.id] && (
                      <div className="mt-2">
                        <ResultText result={results[step.id]} />
                      </div>
                    )}
                  </li>
                ))}
              </ol>
            </Card>
          ))}
        </div>
      </div>
      <Notice tone="info">
        권장 순서: 초기화 → 정상 1·2 → 조건 변경 1(D-034 입금) → 정상 3·4 → 승인 거절 1·2 → 승인 거절 3(D-012 이의) → 조건 변경
        2(D-041 정정). 결정·회신 화면을 보여주려면 해당 단계 대신 그 화면에서 직접 누르면 됩니다.
      </Notice>
    </div>
  )
}

function ResultText({ result }: { result: { ok: boolean; text: string } }) {
  return <span className={`text-xs font-semibold ${result.ok ? 'text-success' : 'text-danger'}`}>{result.text}</span>
}
