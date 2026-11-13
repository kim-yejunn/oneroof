import { useState } from 'react'
import type { Clarification } from '../api.ts'
import { ProvenanceBadge } from '../ui.tsx'

const lines = (value: string | null) => (value ? value.split('\n').filter(Boolean) : [])

/**
 * What the reply interpretation says is unanswered or worth checking, as a checklist for the
 * person deciding. The decision itself stays theirs; which items they checked goes into the
 * decision comment, and from there into the approval record and the audit log.
 */
export function useAiChecklist(source: Clarification | undefined) {
  const items = source
    ? [...lines(source.aiGaps).map((gap) => `미답변: ${gap}`), ...lines(source.aiFollowUps)]
    : []
  const [checked, setChecked] = useState<ReadonlySet<number>>(new Set())
  const toggle = (index: number) =>
    setChecked((previous) => {
      const next = new Set(previous)
      if (next.has(index)) {
        next.delete(index)
      } else {
        next.add(index)
      }
      return next
    })
  /** Appended to the decision comment so the record shows how the interpretation was used. */
  const note = () => {
    if (items.length === 0 || !source) {
      return ''
    }
    const done = items.filter((_, index) => checked.has(index))
    const open = items.filter((_, index) => !checked.has(index))
    return `[회신 해석 #${source.id} 제안 ${done.length}/${items.length}건 확인${done.length ? ': ' + done.join('; ') : ''}${
      open.length ? ' / 미확인: ' + open.join('; ') : ''
    }]`
  }
  return { source, items, checked, toggle, note }
}

export function AiChecklist({ checklist }: { checklist: ReturnType<typeof useAiChecklist> }) {
  const { source, items, checked, toggle } = checklist
  if (!source || items.length === 0) {
    return null
  }
  const ai = source.aiInterpreter !== 'rule-based'
  return (
    <div className="rounded-xl border border-violet/30 bg-violet-soft/40 px-3.5 py-3">
      <div className="mb-2 flex flex-wrap items-center gap-1.5">
        <span className="text-xs font-bold text-violet">결정 전 확인 · 회신 해석 제안</span>
        <ProvenanceBadge code={ai ? 'AI_GENERATED' : 'CALCULATED'} />
        <span className="text-[11px] text-ink-mute">확인한 항목을 체크하면 결정 기록에 남습니다</span>
      </div>
      <ul className="flex flex-col gap-1.5">
        {items.map((item, index) => (
          <li key={item}>
            <label className="flex cursor-pointer items-start gap-2 text-[13px] leading-snug">
              <input type="checkbox" className="mt-0.5" checked={checked.has(index)} onChange={() => toggle(index)} />
              <span className={item.startsWith('미답변') ? 'text-danger' : ''}>{item}</span>
            </label>
          </li>
        ))}
      </ul>
    </div>
  )
}
