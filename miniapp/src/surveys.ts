export type SurveyTarget = 'list' | string | null
export type SurveyQuestion = {
    id: string; type: 'SINGLE' | 'MULTIPLE' | 'TEXT'; title: string; required: boolean
    options: { id: string; label: string }[]
}
export type SurveySummary = {
    id: string;
    title: string;
    description: string;
    status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
    responseCount: number;
    updatedAt: number;
    submitted?: boolean;
    rewardGold: number;
    visible: boolean;
    pinned: boolean;
    repeatable: boolean
}
export type Survey = SurveySummary & { questions: SurveyQuestion[]; revision: number }
export type SurveyAnswer = { answers: Record<string, string[]>; submittedAt: number; rewardGold: number }
export type SurveyDetail = { survey: Survey; submission: SurveyAnswer | null }
export type SurveyPage = { items: SurveySummary[]; total: number }
export type SurveyResult<T> = { code: number; message: string; data: T }

export function surveyTarget(url: URL): SurveyTarget {
    const match = /^\/dashboard\/surveys(?:\/([0-9a-f]{12}))?\/?$/.exec(url.pathname)
    return match ? match[1] || 'list' : null
}

export function surveyPath(target: SurveyTarget): string {
    return target === null ? '/dashboard/' : target === 'list' ? '/dashboard/surveys' : `/dashboard/surveys/${target}`
}
