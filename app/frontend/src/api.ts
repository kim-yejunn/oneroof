export type RoleCode = 'SALES_SUPPORT' | 'SALES_EXECUTIVE'
export type ProvenanceCode = 'ORIGINAL' | 'DEPARTMENT_CLAIM' | 'ASSUMPTION' | 'MOCK_REPLY' | 'CALCULATED'

export interface Session {
  businessDate: string
  roles: { code: RoleCode; label: string }[]
  provenances: { code: ProvenanceCode; label: string }[]
  limitPolicy: { monthlyLimitKrw: number; effectiveFrom: string; provenance: ProvenanceCode; sourceRef: string }
}

export interface Period {
  period: string
  claimCount: number
  totalKrw: number
  pendingCount: number
  underApprovalCount: number
  paymentRequestedCount: number
  paidCount: number
  settledInSourceCount: number
  rejectedCount: number
  batchOpened: boolean
  sourceEventId: string | null
}

export interface ReviewSummary {
  id: number
  period: string
  unitKey: string
  relatedGroup: boolean
  memberAgencyIds: string
  totalKrw: number
  limitKrw: number
  exceedsLimit: boolean
  requiredApprover: RoleCode
  status: string
  title: string
  correlationId: string
  approvalRequestId: number | null
  approvalStatus: string | null
  decidedByRole: RoleCode | null
  decidedByName: string | null
  decisionComment: string | null
  claimCount: number
  paidCount: number
  round: number
  clarificationId: number | null
  clarificationStatus: string | null
}

export interface Claim {
  rebateId: string
  agencyId: string
  agencyName: string
  period: string
  amountKrw: number
  originalStatus: string
  settlementStatus: string
  sourceRef: string
  paymentInstructionId: number | null
  paymentStatus: string | null
  gatewayReference: string | null
  bankReference: string | null
}

export interface AuditEntry {
  id: number
  recordedAt: string
  businessDate: string
  actorRole: string
  action: string
  subjectType: string
  subjectId: string
  detail: string
  provenance: ProvenanceCode
  correlationId: string | null
}

export interface Evidence {
  agencyId: string
  value: string
  basis: 'MASTER_REGISTRATION' | 'OBSERVED_DEPOSIT'
  basisLabel: string
  provenance: ProvenanceCode
  sourceRef: string
}

export interface Link {
  agencyA: string
  agencyB: string
  related: boolean
  relatedInMasterOnly: boolean
  matches: { kind: string; kindLabel: string; left: Evidence; right: Evidence }[]
}

export interface GroupMember {
  agencyId: string
  name: string
  businessRegNo: string
  registeredDate: string
  sourceRef: string
}

export interface HistoryMonth {
  period: string
  totalKrw: number
  limitKrw: number
  exceedsLimit: boolean
  requiredApprover: RoleCode
  originalApprovers: string[]
  statuses: string[]
}

export interface Group {
  groupKey: string
  members: GroupMember[]
  links: Link[]
  history?: HistoryMonth[]
}

export interface ReviewDetail extends ReviewSummary {
  rationale: string
  decidedOn: string | null
  claims: Claim[]
  audit: AuditEntry[]
  evidence?: Group
  history?: HistoryMonth[]
  clarifications: Clarification[]
  approvals: ApprovalHistory[]
}

export interface ApprovalHistory {
  id: number
  title: string
  requiredRole: RoleCode
  status: string
  requestedOn: string
  decidedByRole: string | null
  decidedByName: string | null
  decidedOn: string | null
  decisionComment: string | null
}

export type ClarificationKind = 'RELATION_EXPLANATION' | 'ACCOUNT_CONFIRMATION'
export type ClarificationOutcome = 'EXPLAINED' | 'ACCOUNT_CONFIRMED' | 'ACCOUNT_CHANGED' | 'NO_REPLY'

export interface Clarification {
  id: number
  kind: ClarificationKind
  subjectType: string
  subjectId: string
  agencyIds: string
  title: string
  question: string
  status: 'SENT' | 'ANSWERED' | 'EXPIRED'
  sentOn: string
  dueOn: string
  noticeReference: string | null
  outcome: ClarificationOutcome | null
  replyText: string | null
  replyAccount: string | null
  repliedOn: string | null
  correlationId: string
}

export interface Finding {
  id: number
  period: string
  unitKey: string
  memberAgencyIds: string
  rebateIds: string
  totalKrw: number
  limitKrw: number
  approvedBy: string
  requiredApprover: RoleCode
  status: 'AWAITING_CONFIRMATION' | 'CONFIRMED' | 'REFERRED_TO_AUDIT'
  masterChangeId: number
  title: string
  rationale: string
  correlationId: string
  approvalRequestId: number | null
  approvalStatus: string | null
  decisionComment: string | null
}

export interface Referral {
  id: number
  subjectType: string
  subjectId: string
  title: string
  reason: string
  referredBy: string
  referredOn: string
  status: string
  correlationId: string
}

export type IdentityKindCode = 'REPRESENTATIVE' | 'ADDRESS' | 'BANK_ACCOUNT'

export interface MasterChange {
  id: number
  eventId: string
  agencyId: string
  kind: IdentityKindCode
  previousValue: string
  newValue: string
  effectiveFrom: string
  reason: string
  reportedBy: string
  provenance: ProvenanceCode
  recordedOn: string
}

export interface Identity {
  agencyId: string
  kind: IdentityKindCode
  identityValue: string
  effectiveFrom: string
  effectiveTo: string | null
  provenance: ProvenanceCode
  sourceRef: string
}

export interface PendingApproval {
  id: number
  subjectType: string
  subjectId: string
  requiredRole: RoleCode
  title: string
  amountKrw: number
  exceedsLimit: boolean | null
  relatedGroup: boolean | null
  period: string | null
}

export interface Payment {
  id: number
  referenceId: string
  agencyId: string
  agencyName: string
  amountKrw: number
  status: string
  gatewayReference: string | null
  bankReference: string | null
  period: string | null
  reviewId: number | null
  payeeAccount: string | null
  holdReason: string | null
  clarificationId: number | null
  clarificationStatus: string | null
}

export interface RelatedParties {
  asOf: string
  groups: Group[]
  nearMisses: Link[]
  agencies: { agencyId: string; name: string }[]
}

export class ApiError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) {
    return (await response.json()) as T
  }
  let code = String(response.status)
  let message = `요청이 실패했습니다 (${response.status})`
  try {
    const body = (await response.json()) as { code?: string; message?: string }
    code = body.code ?? code
    message = body.message ?? message
  } catch {
    // The server did not send a JSON error body; keep the generic message.
  }
  throw new ApiError(code, message)
}

export async function get<T>(path: string): Promise<T> {
  return parse<T>(await fetch(`/api${path}`))
}

export async function post<T>(path: string, role: RoleCode, body: unknown): Promise<T> {
  return parse<T>(
    await fetch(`/api${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Oneroof-Role': role },
      body: JSON.stringify(body),
    }),
  )
}
